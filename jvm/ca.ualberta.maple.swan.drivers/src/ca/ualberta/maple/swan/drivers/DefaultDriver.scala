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

import java.io.{File, FileWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.{CanModule, Module, ModuleGroup, ModuleGrouper, SWIRLParser, SWIRLPrinter, SWIRLPrinterOptions}
import ca.ualberta.maple.swan.parser.SILParser
import ca.ualberta.maple.swan.utils.Logging
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.collection.mutable.ArrayBuffer

object DefaultDriver {

  class DriverOptions {
    var printSwirl = true
    def printSwirl(v: Boolean): DriverOptions = {
      this.printSwirl = v
      this
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      throw new RuntimeException("Expected 1 argument: the swan-dir file path")
    }
    run(new File(args(0)), new DriverOptions)
  }

  def writeFile(module: Object, debugDir: File, prefix: String): Unit = {
    val printedSwirlModule = {
      module match {
        case canModule: CanModule =>
          new SWIRLPrinter().print(canModule, new SWIRLPrinterOptions)
        case rawModule: Module =>
          new SWIRLPrinter().print(rawModule, new SWIRLPrinterOptions)
        case groupModule: ModuleGroup =>
          new SWIRLPrinter().print(groupModule, new SWIRLPrinterOptions)
        case _ =>
          throw new RuntimeException("unexpected")
      }
    }
    val f = Paths.get(debugDir.getPath, prefix + ".swirl").toFile
    val fw = new FileWriter(f)
    fw.write(printedSwirlModule)
    fw.close()
  }

  def runner(debugDir: File, file: File, options: DriverOptions): CanModule = {
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    val swirlModule = new SWIRLGen().translateSILModule(silModule)
    if (options.printSwirl) writeFile(swirlModule, debugDir, file.getName + ".raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.printSwirl) writeFile(canSwirlModule, debugDir, file.getName)
    canSwirlModule
  }

  def modelRunner(debugDir: File, modelsContent: String, options: DriverOptions): CanModule = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    if (options.printSwirl) writeFile(swirlModule, debugDir, "models.raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.printSwirl) writeFile(canSwirlModule, debugDir, "models")
    canSwirlModule
  }

  def run(swanDir: File, options: DriverOptions): ModuleGroup = {
    val dirProcessor = new DirProcessor(swanDir.toPath)
    val silFiles = dirProcessor.process()
    val threads = new ArrayBuffer[Thread]()
    val modules = new ArrayBuffer[CanModule]()
    val debugDir = Files.createDirectories(
      Paths.get(swanDir.getPath, "debug-dir")).toFile
    FileUtils.cleanDirectory(debugDir)
    silFiles.foreach(f => {
      val t = new Thread {
        override def run(): Unit = {
          modules.append(runner(debugDir, f, options))
        }
      }
      threads.append(t)
      t.start()
    })
    // Single file for now, iterating files is tricky with JAR resources)
    val in = DefaultDriver.getClass.getClassLoader.getResourceAsStream("models.swanir")
    val modelsContent = IOUtils.toString(in, StandardCharsets.UTF_8)
    val t = new Thread {
      override def run(): Unit = {
        modules.append(modelRunner(debugDir, modelsContent, options))
      }
    }
    threads.append(t)
    t.start()
    threads.foreach(f => f.join())
    val group = ModuleGrouper.group(modules)
    Logging.printInfo("Group ready:\n"+group.toString+group.functions.length+" functions")
    if (options.printSwirl) writeFile(group, debugDir, "group")
    group
  }
}
