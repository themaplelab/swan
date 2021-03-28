/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.drivers

import java.io.File

import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.{CanModule, ModuleGroup, ModuleGrouper, SWIRLParser}
import ca.ualberta.maple.swan.parser.SILParser
import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable.ArrayBuffer

object DefaultDriver {

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      throw new RuntimeException("Expected 1 argument: the swan-dir file path")
    }
    run(new File(args(0)))
  }

  def runner(file: File): CanModule = {
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    val swirlModule = new SWIRLGen().translateSILModule(silModule)
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    canSwirlModule
  }

  def modelRunner(file: File): CanModule = {
    val swirlModule = new SWIRLParser(file.toPath).parseModule()
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    canSwirlModule
  }

  def run(swanDir: File): ModuleGroup = {
    val dirProcessor = new DirProcessor(swanDir.toPath)
    val silFiles = dirProcessor.process()
    val threads = new ArrayBuffer[Thread]()
    val modules = new ArrayBuffer[CanModule]()
    silFiles.foreach(f => {
      val t = new Thread {
        override def run(): Unit = {
          modules.append(runner(f))
        }
      }
      threads.append(t)
      t.start()
    })
    // Single file for now (models/*.swanir is tricky with JAR resources)
    /*val in = getClass.getClassLoader.getResourceAsStream("models.swanir")
    val br = new BufferedReader(new InputStreamReader(in))
    val t = new Thread {
      override def run(): Unit = {
        // modules.append(modelRunner(modelsFile))
      }
    }
    threads.append(t)
    t.start()*/
    threads.foreach(f => f.join())
    val group = ModuleGrouper.group(modules)
    Logging.printInfo("Group ready:\n"+group.toString+group.functions.length+" functions")
    group
  }
}
