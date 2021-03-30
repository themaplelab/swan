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
import org.apache.commons.io.IOUtils

import scala.collection.mutable.ArrayBuffer

object DefaultDriver {

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      throw new RuntimeException("Expected 1 argument: the swan-dir file path")
    }
    run(new File(args(0)))
  }

  def writeFile(module: Object, intermediateDir: File, prefix: String): Unit = {
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
    val f = Paths.get(intermediateDir.getPath, prefix + ".swirl").toFile
    val fw = new FileWriter(f)
    fw.write(printedSwirlModule)
    fw.close()
  }

  def runner(intermediateDir: File, file: File, threads: ArrayBuffer[Thread]): CanModule = {
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    val swirlModule = new SWIRLGen().translateSILModule(silModule)
    val rawPt = new Thread() {
      override def run(): Unit = {
        writeFile(swirlModule, intermediateDir, file.getName + ".raw")
      }
    }
    threads.append(rawPt)
    rawPt.start()
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    val canPt = new Thread() {
      override def run(): Unit = {
        writeFile(canSwirlModule, intermediateDir, file.getName)
      }
    }
    threads.append(canPt)
    canPt.start()
    canSwirlModule
  }

  def modelRunner(intermediateDir: File, modelsContent: String, threads: ArrayBuffer[Thread]): CanModule = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    val pt = new Thread() {
      override def run(): Unit = {
        writeFile(swirlModule, intermediateDir, "models.raw")
      }
    }
    threads.append(pt)
    pt.start()
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    val canPt = new Thread() {
      override def run(): Unit = {
        writeFile(canSwirlModule, intermediateDir, "models.raw")
      }
    }
    threads.append(canPt)
    canPt.start()
    canSwirlModule
  }

  def run(swanDir: File): ModuleGroup = {
    val dirProcessor = new DirProcessor(swanDir.toPath)
    val silFiles = dirProcessor.process()
    val threads = new ArrayBuffer[Thread]()
    val modules = new ArrayBuffer[CanModule]()
    val intermediateDir = Files.createDirectories(
      Paths.get(swanDir.getPath, "intermediate-dir")).toFile
    silFiles.foreach(f => {
      val t = new Thread {
        override def run(): Unit = {
          modules.append(runner(intermediateDir, f, threads))
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
        modules.append(modelRunner(intermediateDir, modelsContent, threads))
      }
    }
    threads.append(t)
    t.start()
    threads.foreach(f => f.join())
    val group = ModuleGrouper.group(modules)
    Logging.printInfo("Group ready:\n"+group.toString+group.functions.length+" functions")
    writeFile(group, intermediateDir, "group")
    group
  }
}
