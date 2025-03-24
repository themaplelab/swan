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
  static let defaultSwanDir = URL(fileURLWithPath: "swan-dir/")
  static let swiftcLog = "swiftc.log"
  static let defaultSDK = SDK.macosx
}

enum SDK: String, ExpressibleByArgument {
  case driverkit = "driverkit"
  case ios = "iphoneos"
  case iphonesimulator = "iphonesimulator"
  case macosx = "macosx"
  case appletvos = "appletvos"
  case appletvsimulator = "appletvsimulator"
  case xros = "xros"
  case watchos = "watchos"
  case watchsimulator = "watchsimulator"
}

extension URL: @retroactive ExpressibleByArgument {
  public init(argument: String) {
    self = URL(fileURLWithPath: argument).absoluteURL
  }
}

@main
struct SWANSwiftcBuild: ParsableCommand {
  static let configuration = CommandConfiguration(
    abstract: "Build and dump SIL for a Swift application using swiftc."
  )

  @Option(help: "Output directory for SIL.")
  var swanDir: URL = Constants.defaultSwanDir

  @Argument(help: "Additional arguments to pass to swiftc. Prefix these arguments with --")
  var swiftcArgs: [String]

  @Option(help: "The SDK to use.")
  var sdk: SDK = Constants.defaultSDK

  lazy var srcCopyDir = swanDir.appendingPathComponent("src")
  lazy var swiftcLog = swanDir.appendingPathComponent(Constants.swiftcLog)

  func generateSwiftcArgs() -> [String] {
    ["xcrun", "--sdk", self.sdk.rawValue, "swiftc"] + self.swiftcArgs + [
      "-emit-sil",
      "-Xfrontend",
      "-sil-based-debuginfo",
      "-Xllvm",
      "-sil-print-debuginfo",
      "-Xllvm",
      "-sil-print-before=SerializeSILPass",
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

  mutating func validate() throws {
    do {
      try FileManager.default.createDirectory(at: swanDir, withIntermediateDirectories: true)
    } catch {
      printFailure(
        "The output directory could not be created at " + swanDir.path
          + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }

    try? FileManager.default.removeItem(at: srcCopyDir)

    do {
      try FileManager.default.createDirectory(at: srcCopyDir, withIntermediateDirectories: true)
    } catch {
      printFailure(
        "The src directory could not be created at " + srcCopyDir.path
          + ".\nReason: " + error.localizedDescription)
      throw ExitCode.failure
    }
  }

  mutating func runSwiftC() throws -> String {
    let args = generateSwiftcArgs()
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

    task.waitUntilExit()

    let output: String = String(data: data, encoding: String.Encoding.utf8)!

    let end = DispatchTime.now()
    let nanoTime = (end.uptimeNanoseconds - start.uptimeNanoseconds)
    let timeInterval = Int(round(Double(nanoTime) / 1_000_000_000))

    printStatus("\nswiftc finished in \(timeInterval.description)s")

    do {
      try output.write(to: swiftcLog, atomically: true, encoding: String.Encoding.utf8)
      printStatus("swiftc output written to \(Constants.swiftcLog)")
    } catch {
      printFailure(
        "Could not write swiftc output to " + Constants.swiftcLog + "\nReason: "
          + error.localizedDescription)
      throw ExitCode.failure
    }

    if task.terminationStatus != 0 {
      printFailure("\nswiftc failed. Please see \(swiftcLog.relativeString)\n")
      throw ExitCode.failure
    }

    print("")

    return output
  }

  mutating func run() throws {
    var outputSilFileName = "out.sil"

    // swan-swiftc doesn't actually expect multiple source files
    // I'm not sure what the output would look like for that
    try self.swiftcArgs.forEach { (str) in
      if str.hasSuffix(".swift") {
        let path = URL(fileURLWithPath: str)

        outputSilFileName = path.lastPathComponent + ".sil"
        let copyPath = srcCopyDir.appendingPathComponent(path.lastPathComponent)

        do {
          try FileManager.default.copyItem(at: path, to: copyPath)
        } catch {
          printFailure(
            "Could not copy file from \(path) to \(copyPath)\nReason: \(error.localizedDescription)"
          )
          throw ExitCode.failure
        }
      }
    }

    let output = try runSwiftC()

    var sil = output.components(separatedBy: "\nsil_stage canonical")[1]
    sil = "sil_stage canonical\(sil)\n\n"

    let filename = swanDir.appendingPathComponent(outputSilFileName)
    do {
      try sil.write(to: filename, atomically: true, encoding: String.Encoding.utf8)
    } catch {
      printFailure("Could not write SIL to \(filename)\nReason: \(error.localizedDescription)")
    }

    // Delete unneeded generated file '-.sil_dbg_0.sil'
    try? FileManager().removeItem(at: URL(fileURLWithPath: "-.sil_dbg_0.sil"))

    printStatus("\nSIL written to \(swanDir.path)")
  }
}
