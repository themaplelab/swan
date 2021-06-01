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
    return ["swiftc"] + self.swiftcArgs + [
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

    var outputSilFileName = "out.sil"
        
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
    
    // swan-swiftc doesn't actually expect multiple source files
    // I'm not sure what the output would look like for that
    try self.swiftcArgs.forEach { (str) in
      if (str.hasSuffix(".swift")) {
        outputSilFileName = str + ".sil"
        try FileManager().copyItem(atPath: URL(fileURLWithPath: str).path, toPath: srcCopyDir.appendingPathComponent(str).path)
      }
    }
    
    let args = generateSwiftcArgs()
    printStatus("Running swiftc " + args.joined(separator: " "))
    
    let task = Process()
    let pipe = Pipe()
    
    task.launchPath = URL(string: "/usr/bin/env")?.absoluteString
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
      printFailure("\nswiftc failed. Please see \(swiftcLog.relativeString)\n")
      throw ExitCode.failure
    }
    
    print("")
    
    var sil = output.components(separatedBy: "\nsil_stage canonical")[1]
    sil = "sil_stage canonical\(sil)\n\n"
    
    let filename = outputDir.appendingPathComponent(outputSilFileName)
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
