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

package ca.ualberta.maple.swan.utils
import sys.process.*

object SwiftDemangleLocator {

  def swiftDemangleLocation(): String = {
    var swiftDemangleBinName = "swift-demangle"
    val result = s"which $swiftDemangleBinName".!

    if (result == 0) { return s"which $swiftDemangleBinName".!!.trim }

    val osName: String = System.getProperty("os.name").toLowerCase
    val isMacOs: Boolean = osName.startsWith("mac os x")
    // Can't resolve on Linux. Checker will throw expection.
    if (!isMacOs) { return null }

    // Get path to the active developer directory.
    var selectedXcode = "xcode-select -p".!!.trim
    var swiftDemanglePath: String = null
    // If it's command line tools
    if (selectedXcode.startsWith("/Library/Developer/CommandLineTools")) {
      swiftDemanglePath = selectedXcode + s"/usr/bin/$swiftDemangleBinName"
    // If it's Xcode.app
    } else if (selectedXcode.endsWith(".app/Contents/Developer")) {
      swiftDemanglePath = selectedXcode + s"/Toolchains/XcodeDefault.xctoolchain/usr/bin/$swiftDemangleBinName"
    }

    return swiftDemanglePath
  }
}
