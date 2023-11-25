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
import System
import SwanSwiftBuildLogParser

struct Constants {
  static let defaultSwanDir = "swan-dir/"
}

struct SWANXcodebuild: ParsableCommand, Logger {
  static let configuration = CommandConfiguration(
    abstract: "Dump SIL for a Swift application from xcodebuild log.")

  // Ignore the warning generated from this.
  @Option(default: Constants.defaultSwanDir, help: "Output directory for SIL.")
  var swanDir: String

  @Option(help: "xcodebuild log file.")
  var xcodebuildLog: String

  init() { }

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

    let sections = try SwanSwiftBuildLogParser.parse(xcodebuildLog: xcodebuildLog, logger: self)
    try SwanSwiftBuildLogParser.save(sections: sections, to: outputDir, logger: self)
  }

}

SWANXcodebuild.main()
