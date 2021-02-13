import ArgumentParser
import ColorizeSwift
import Foundation

struct Constants {
  static let defaultSwanSIL = "swan-sil/"
  static let xcodebuildFile = "/usr/bin/xcodebuild"
  static let xcodebuildLog  = "xcodebuild.log"
}

struct Section {
  let platform: String
  let target: String
  let project: String
  let sil: String
}

extension String: Error {}

func ==(lhs: Section, rhs: Section) -> Bool {
  // Do NOT include platform. Might not be necesssary to compare SIL.
  return ((lhs.target == rhs.target) && (lhs.project == rhs.project) && (lhs.sil == rhs.sil))
}

struct SWANXcodeBuild: ParsableCommand {
  static let configuration = CommandConfiguration(
    abstract: "Build and dump SIL for a Swift application using xcodebuild.")
  
  // Ignore the warning generated from this.
  @Option(default: Constants.defaultSwanSIL, help: "Output directory for SIL.")
  var silDir: String?
  
  @Argument(help: "Prefix these arguments with --")
  var xcodebuildArgs: [String]
  
  init() { }
  
  func generateXcodebuildArgs() -> [String] {
    return self.xcodebuildArgs + [
      "SWIFT_COMPILATION_MODE=wholemodule",
      "CODE_SIGN_IDENTITY=\"\"",
      "CODE_SIGNING_REQUIRED=NO",
      "CODE_SIGNING_ALLOWED=NO",
      "OTHER_SWIFT_FLAGS=-Xfrontend -gsil -Xllvm -sil-print-debuginfo -Xllvm -sil-print-before=SerializeSILPass"
    ]
  }
  
  func printStatus(_ msg: String) {
    print(msg.foregroundColor(.steelBlue1_2).bold())
  }
  
  func printFailure(_ msg: String) {
    print(msg.foregroundColor(.red).bold())
  }
  
  func printWarning(_ msg: String) {
    print(msg.foregroundColor(.orange3).bold())
  }
  
  func run() throws {

    let outputDir = URL(fileURLWithPath: self.silDir!)
    
    let xcodebuildLog = outputDir.appendingPathComponent(Constants.xcodebuildLog)
        
    do {
      try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    } catch {
      printFailure("The output directory could not be created at " + outputDir.absoluteString
                    + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }
    
    let args = generateXcodebuildArgs()
    printStatus("Running xcodebuild " + args.joined(separator: " "))
    
    let task = Process()
    let pipe = Pipe()
    
    task.launchPath = URL(string: Constants.xcodebuildFile)?.absoluteString
    task.arguments = args
    task.standardInput = FileHandle.nullDevice
    task.standardOutput = pipe
    task.standardError = pipe
    
    let start = DispatchTime.now()
    task.launch()
    
    let data = pipe.fileHandleForReading.readDataToEndOfFile()
    let output: String = String(data: data, encoding: String.Encoding.utf8)!
    
    let end = DispatchTime.now()
    let nanoTime = (end.uptimeNanoseconds - start.uptimeNanoseconds)
    let timeInterval = Int(round(Double(nanoTime) / 1_000_000_000))
    
    printStatus("\nxcodebuild finished in \(timeInterval.description)s")
    
    do {
      try output.write(to: xcodebuildLog, atomically: true, encoding: String.Encoding.utf8)
      printStatus("xcodebuild output written to \(Constants.xcodebuildLog)")
    } catch {
      printFailure("Could not write xcodebuild output to " + Constants.xcodebuildLog + "\nReason: " + error.localizedDescription)
      return
    }
    
    if (task.terminationStatus != 0) {
      printWarning("\nxcodebuild failed. Please see \(xcodebuildLog.relativeString)\n")
      return
    }
    
    print("")
    
    var roughSections = output.components(separatedBy: "\nCompileSwift normal ")
    roughSections.removeFirst()
    var sections = [Section]()
    
    for s in roughSections {
      // Quick and dirty parsing
      let chars: [Character] = Array(s[s.startIndex...s.firstIndex(of: "\n")!])
      var cursor: Int = 0
      let platform = String(chars.suffix(from: cursor).prefix(while: { (character) -> Bool in
        return character != " "
      }))
      cursor += platform.count + 1
      // Some sources have a Swift file path.
      if (chars[cursor] != "(") {
        let path = String(chars.suffix(from: cursor).prefix(while: { (character) -> Bool in
          return character != " "
        }))
        cursor += path.count + 1
      }
      var expected = "(in target '"
      if (!chars.suffix(from: cursor).starts(with: expected)) {
        throw "parsing error: target expected\n\(String(chars))"
      }
      cursor += expected.count
      let target = String(chars.suffix(from: cursor).prefix(while: { (character) -> Bool in
        return character != "'"
      }))
      cursor += target.count
      expected = "' from project '"
      if (!chars.suffix(from: cursor).starts(with: expected)) {
        throw "parsing error: project expected\n\(String(chars))"
      }
      cursor += expected.count
      let project = String(chars.suffix(from: cursor).prefix(while: { (character) -> Bool in
        return character != "'"
      }))
      // Isolate SIL from rest of output
      var sil = s.components(separatedBy: "\nsil_stage canonical")[1].components(separatedBy: "\n\n\n\n")[0]
      sil = "sil_stage canonical\(sil)\n\n"
      let newSection = Section(platform: platform, target: target, project: project, sil: sil)
      print("Detected compilation unit\n  target: \(target)\n  project: \(project)\n  platform: \(platform)\n  SIL lines: \(sil.components(separatedBy: "\n").count)\n")
      if (!sections.contains(where: { (section) -> Bool in return section == newSection})) {
        sections.append(newSection)
      } else {
        print("Ignored section because it already exists under another platform.")
      }
    }
    
    for section in sections {
      let filename = outputDir.appendingPathComponent("\(section.target).\(section.project).sil")
      do {
        if FileManager.default.fileExists(atPath: filename.path) {
          let existing = try String(contentsOf: filename)
          // Write and compares files instead. This is temporary because comparing strings does not work reliably.
          if (existing.components(separatedBy: "\n").count != section.sil.components(separatedBy: "\n").count) {
            printStatus("Detected change: \(section.target).\(section.project)")
          }
        }
        try section.sil.write(to: filename, atomically: true, encoding: String.Encoding.utf8)
      } catch {
        printFailure("Could not write SIL to \(filename)\nReason: \(error.localizedDescription)")
      }
    }
    
    printStatus("\nSIL written to \(outputDir.path)")
  }
  
}

SWANXcodeBuild.main()

