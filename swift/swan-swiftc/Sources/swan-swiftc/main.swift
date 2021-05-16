import ArgumentParser
import ColorizeSwift
import Foundation

struct Constants {
  static let defaultSwanDir = "swan-dir/"
  static let swiftcFile = "/usr/bin/swiftc"
  static let swiftcLog  = "swiftc.log"
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

struct SWANSwiftcBuild: ParsableCommand {
  static let configuration = CommandConfiguration(
    abstract: "Build and dump SIL for a Swift application using swiftc.")
  
  // Ignore the warning generated from this.
  @Option(default: Constants.defaultSwanDir, help: "Output directory for SIL.")
  var swanDir: String?
  
  @Argument(help: "Prefix these arguments with --")
  var swiftcArgs: [String]
  
  init() { }
  
  func generateSwiftcArgs() -> [String] {
    return self.swiftcArgs + [
      "-emit-sil",
      "-Xfrontend",
      "-gsil",
      "-Xllvm",
      "-sil-print-debuginfo",
      "-Xllvm",
      "-sil-print-before=SerializeSILPass"
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

    let outputDir = URL(fileURLWithPath: self.swanDir!)
    let srcCopyDir = outputDir.appendingPathComponent("src")
    
    let swiftcLog = outputDir.appendingPathComponent(Constants.swiftcLog)
        
    do {
      try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    } catch {
      printFailure("The output directory could not be created at " + outputDir.path
                    + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }
    
    do {
      try FileManager.default.createDirectory(at: srcCopyDir, withIntermediateDirectories: true)
    } catch {
      printFailure("The src directory could not be created at " + srcCopyDir.path
                    + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }
    
    try self.swiftcArgs.forEach { (str) in
      if (str.hasSuffix(".swift")) {
        try FileManager().copyItem(atPath: URL(fileURLWithPath: str).path, toPath: srcCopyDir.appendingPathComponent(str).path)
      }
    }
    
    let args = generateSwiftcArgs()
    printStatus("Running swiftc " + args.joined(separator: " "))
    
    let task = Process()
    let pipe = Pipe()
    
    task.launchPath = URL(string: Constants.swiftcFile)?.absoluteString
    task.arguments = args
    task.standardInput = FileHandle.nullDevice
    task.standardOutput = pipe
    task.standardError = pipe
    
    let start = DispatchTime.now()
    task.launch()
    task.waitUntilExit()
    
    let data = pipe.fileHandleForReading.readDataToEndOfFile()
    let output: String = String(data: data, encoding: String.Encoding.utf8)!
    
    let end = DispatchTime.now()
    let nanoTime = (end.uptimeNanoseconds - start.uptimeNanoseconds)
    let timeInterval = Int(round(Double(nanoTime) / 1_000_000_000))
    
    printStatus("\nswiftc finished in \(timeInterval.description)s")
    
    do {
      try output.write(to: swiftcLog, atomically: true, encoding: String.Encoding.utf8)
      printStatus("swiftc output written to \(Constants.swiftcLog)")
    } catch {
      printFailure("Could not write swiftc output to " + Constants.swiftcLog + "\nReason: " + error.localizedDescription)
      return
    }
    
    if (task.terminationStatus != 0) {
      printWarning("\nswiftc failed. Please see \(swiftcLog.relativeString)\n")
      return
    }
    
    print("")
    
    var sil = output.components(separatedBy: "\nsil_stage canonical")[1]
    sil = "sil_stage canonical\(sil)\n\n"
    
    let filename = outputDir.appendingPathComponent("out.sil")
    do {
      try sil.write(to: filename, atomically: true, encoding: String.Encoding.utf8)
    } catch {
      printFailure("Could not write SIL to \(filename)\nReason: \(error.localizedDescription)")
    }
    
    // Delete generated unneeded generate file '-.gsil_0.sil'
    try FileManager().removeItem(at: URL(fileURLWithPath: "-.gsil_0.sil"))
    
    printStatus("\nSIL written to \(outputDir.path)")
  }
  
}

SWANSwiftcBuild.main()
