/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.test

import java.io.File
import java.nio.charset.StandardCharsets

import ca.ualberta.maple.swan.drivers.DefaultDriver
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.{CanModule, Module, ModuleGroup, ModuleGrouper, SWIRLParser}
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

  private def getModelModule(): CanModule = {
    val in = DefaultDriver.getClass.getClassLoader.getResourceAsStream("models.swanir")
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
    modules.append(getModelModule())
    val group = ModuleGrouper.group(modules)
    group
  }
}
