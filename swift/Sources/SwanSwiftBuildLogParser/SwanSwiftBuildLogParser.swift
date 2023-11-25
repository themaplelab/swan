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

import Foundation
import System

public struct Section: Equatable {
  public let platform: String
  public let target: String
  public let project: String
  public let sil: String

  public static func ==(lhs: Section, rhs: Section) -> Bool {
    // Do NOT include platform. Might not be necesssary to compare SIL.
    return ((lhs.target == rhs.target) && (lhs.project == rhs.project) && (lhs.sil == rhs.sil))
  }
}

extension String: Error {}

enum ExitCode: Int32, Swift.Error {
  case failure = 1
}

public protocol Logger {
  func printFailure(_ message: String)
  func printStatus(_ message: String)
  func print(_ items: Any..., separator: String, terminator: String)
}

extension Logger {
  public func print(_ items: Any...) {
    print(items, separator: " ", terminator: "\n")
  }

  public func print(_ items: Any..., separator: String, terminator: String) {
    Swift.print(items, separator: separator, terminator: terminator)
  }
}

public enum SwanSwiftBuildLogParser {
  public static func parse(xcodebuildLog: String, logger: Logger) throws -> [Section] {
    let output = String(decoding: try Data(contentsOf: URL(fileURLWithPath: xcodebuildLog)), as: UTF8.self)

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
      let silStages = s.components(separatedBy: "\nsil_stage canonical")
      // skip if there is no SIL
      if silStages.count < 2 {
        continue
      }
      var sil = silStages[1].components(separatedBy: "\n\n\n\n")[0]
      sil = "sil_stage canonical\(sil)\n\n"
      let newSection = Section(platform: platform, target: target, project: project, sil: sil)
      logger.print("Detected compilation unit\n  target: \(target)\n  project: \(project)\n  platform: \(platform)\n  SIL lines: \(sil.components(separatedBy: "\n").count)\n")
      if (!sections.contains(where: { (section) -> Bool in return section == newSection})) {
        sections.append(newSection)
      } else {
        logger.print("Ignored section because it already exists under another platform.")
      }
    }

    return sections
  }

  public static func save(sections: [Section], to outputDir: URL, logger: Logger) throws {
    for section in sections {
      let filename = outputDir.appendingPathComponent("\(section.target).\(section.project).sil")
      do {
        try section.sil.write(to: filename, atomically: true, encoding: String.Encoding.utf8)
      } catch {
        logger.printFailure("Could not write SIL to \(filename)\nReason: \(error.localizedDescription)")
      }
    }

    logger.printStatus("\nSIL written to \(outputDir.path)")
  }
}
