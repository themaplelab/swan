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

import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.{CanModule, Module}
import ca.ualberta.maple.swan.parser.{SILModule, SILParser}

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

  // Single .sil file
  def run(file: File, options: TestDriverOptions): Unit = {
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
  }
}
