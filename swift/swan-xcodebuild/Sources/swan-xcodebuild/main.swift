/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

import ArgumentParser
import ColorizeSwift
import Foundation

struct Constants {
  static let defaultSwanDir = "swan-dir/"
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

struct SWANXcodebuild: ParsableCommand {
  static let configuration = CommandConfiguration(
    abstract: "Build and dump SIL for a Swift application using xcodebuild.")

  // Ignore the warning generated from this.
  @Option(default: Constants.defaultSwanDir, help: "Output directory for SIL.")
  var swanDir: String?

  @Flag(help: "Attempt to parse the build output even if xcodebuild fails.")
  var allowFailure: Bool

  @Argument(help: "Prefix these arguments with --")
  var xcodebuildArgs: [String]

  init() { }

  func generateXcodebuildArgs() -> [String] {
    return ["xcodebuild"] + self.xcodebuildArgs + [
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

    let outputDir = URL(fileURLWithPath: self.swanDir!)
    let srcCopyDir = outputDir.appendingPathComponent("src")

    let xcodebuildLog = outputDir.appendingPathComponent(Constants.xcodebuildLog)

    do {
      try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)
    } catch {
      printFailure("The output directory could not be created at " + outputDir.path
                    + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }

    if (FileManager().fileExists(atPath: srcCopyDir.path)) {
      try FileManager().removeItem(atPath: srcCopyDir.path)
    }

    do {
      try FileManager.default.createDirectory(at: srcCopyDir, withIntermediateDirectories: true)
    } catch {
      printFailure("The src directory could not be created at " + srcCopyDir.path
                    + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }

    try self.xcodebuildArgs.forEach { (str) in
      if (str.contains(".xcodeproj") || str.contains(".xcworkspace")) {
        let searchPath = URL(fileURLWithPath: str).appendingPathComponent("..")
        if let e = FileManager().enumerator(at: searchPath, includingPropertiesForKeys: [.isRegularFileKey], options: [.skipsHiddenFiles, .skipsPackageDescendants]) {
          for case let fileURL as URL in e {
            if (fileURL.pathComponents.contains("swan-dir")) { continue }
            do {
              let fileAttributes = try fileURL.resourceValues(forKeys:[.isRegularFileKey])
              if fileAttributes.isRegularFile! && fileURL.path.hasSuffix(".swift") {
                var strippedPath = String(fileURL.path)
                strippedPath.removeFirst(searchPath.path.count)
                let destinationDir = srcCopyDir.appendingPathComponent(strippedPath).appendingPathComponent("..")
                do {
                  try FileManager.default.createDirectory(at: destinationDir, withIntermediateDirectories: true)
                } catch {
                  printFailure("A src directory could not be created.\nReason: " + error.localizedDescription)
                  throw ExitCode.failure
                }
                try FileManager().copyItem(atPath: fileURL.path, toPath: srcCopyDir.appendingPathComponent(strippedPath).path)
              }
            }
          }
        }
      }
    }


    let args = generateXcodebuildArgs()
    printStatus("Running " + args.joined(separator: " "))

    let task = Process()
    let pipe = Pipe()

    task.launchPath = URL(string: "/usr/bin/env")?.absoluteString
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
      throw ExitCode.failure
    }

    if (task.terminationStatus != 0) {
      printWarning("\nxcodebuild failed. Please see \(xcodebuildLog.relativeString)\n")
      if (allowFailure) {
        printStatus("--allow-failure enabled, continuing")
      } else {
        throw ExitCode.failure
      }
    }

    print("")

    var roughSections = output.components(separatedBy: "\nCompileSwift normal ")
    roughSections.removeFirst()
    var sections = [Section]()

    for (idx, s) in roughSections.enumerated() {
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
          return character != "("
        }))
        cursor += path.count
      }
      var expected = "(in target '"
      if (!chars.suffix(from: cursor).starts(with: expected)) {
        throw "parsing error: section: \(idx), target expected\n\(String(chars))"
      }
      cursor += expected.count
      let target = String(chars.suffix(from: cursor).prefix(while: { (character) -> Bool in
        return character != "'"
      }))
      cursor += target.count
      expected = "' from project '"
      if (!chars.suffix(from: cursor).starts(with: expected)) {
        throw "parsing error: section: \(idx), project expected\n\(String(chars))"
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
        try section.sil.write(to: filename, atomically: true, encoding: String.Encoding.utf8)
      } catch {
        printFailure("Could not write SIL to \(filename)\nReason: \(error.localizedDescription)")
      }
    }

    printStatus("\nSIL written to \(outputDir.path)")
  }

}

SWANXcodebuild.main()
