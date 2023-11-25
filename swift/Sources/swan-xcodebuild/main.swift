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
import SwanSwiftBuildLogParser

struct Constants {
  static let defaultSwanDir = "swan-dir/"
  static let xcodebuildLog  = "xcodebuild.log"
}

struct SWANXcodebuild: ParsableCommand, Logger {
  static let configuration = CommandConfiguration(
    abstract: "Build and dump SIL for a Swift application using xcodebuild.")

  // Ignore the warning generated from this.
  @Option(default: Constants.defaultSwanDir, help: "Output directory for SIL.")
  var swanDir: String

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

    let outputDir = URL(fileURLWithPath: self.swanDir)
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

    let sections = try SwanSwiftBuildLogParser.parse(xcodebuildLog: String(xcodebuildLog.path), logger: self)
    try SwanSwiftBuildLogParser.save(sections: sections, to: outputDir, logger: self)

  }

}

SWANXcodebuild.main()
