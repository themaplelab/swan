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

package ca.ualberta.maple.swan.test

import java.io.File
import java.nio.charset.StandardCharsets

import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.parser.{SILModule, SILParser}
import org.apache.commons.io.IOUtils

import scala.collection.mutable.ArrayBuffer

object TestDriver {

  class TestDriverOptions() {
    var silModuleCB: SILModule => Unit = _
    var rawSwirlModuleCB: Module => Unit = _
    var canSwirlModuleCB: CanModule => Unit = _
    def addSILCallBack(cb: SILModule => Unit): TestDriverOptions = {
      silModuleCB = cb
      this
    }
    def addRawSWIRLCallBack(cb: Module => Unit): TestDriverOptions = {
      rawSwirlModuleCB = cb
      this
    }
    def addCanSWIRLCallBack(cb: CanModule => Unit): TestDriverOptions = {
      canSwirlModuleCB = cb
      this
    }
  }

  def getModelModule: CanModule = {
    val in = this.getClass.getClassLoader.getResourceAsStream("models.swirl")
    val modelsContent = IOUtils.toString(in, StandardCharsets.UTF_8)
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    canSwirlModule
  }

  // Single .sil file
  def run(file: File, options: TestDriverOptions): ModuleGroup = {
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    if (options.silModuleCB != null) {
      options.silModuleCB(silModule)
    }
    val swirlModule = new SWIRLGen().translateSILModule(silModule)
    if (options.rawSwirlModuleCB != null) {
      options.rawSwirlModuleCB(swirlModule)
    }
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.canSwirlModuleCB != null) {
      options.canSwirlModuleCB(canSwirlModule)
    }
    val modules = ArrayBuffer.empty[CanModule]
    modules.append(canSwirlModule)
    modules.append(getModelModule)
    val group = ModuleGrouper.group(modules)
    group
  }
}
