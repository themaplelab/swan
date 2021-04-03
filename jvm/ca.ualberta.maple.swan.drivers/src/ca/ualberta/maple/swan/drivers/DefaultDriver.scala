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
import ca.ualberta.maple.swan.parser.{SILModule, SILParser}
import ca.ualberta.maple.swan.utils.Logging
import org.apache.commons.io.{FileUtils, IOUtils}
import picocli.CommandLine
import picocli.CommandLine.{Command, Option}

import scala.collection.mutable.ArrayBuffer

object DefaultDriver {
  class Options {
    var printSwirl = true
    def printSwirl(v: Boolean): Options = {
      this.printSwirl = v
      this
    }
    var persistence = false
    def persistence(v: Boolean): Options = {
      this.persistence = v
      this
    }
  }

  def main(args: Array[String]): Unit = {
    val exitCode = new CommandLine(new DefaultDriver).execute(args:_*)
    System.exit(exitCode);
  }
}

@Command(name = "SWAN Driver")
class DefaultDriver extends Runnable {

  @Option(names = Array("-s", "--single"),
    description = Array("Use a single thread."))
  private val single = new Array[Boolean](0)

  @Option(names = Array("-d", "--debug"),
    description = Array("Print intermediate representations to debug directory."))
  private val debug = new Array[Boolean](0)

  @Option(names = Array("-c", "--invalidate-cache"),
    description = Array("Invalidate cache."))
  private val invalidateCache = new Array[Boolean](0)

  @Option(names = Array("-p", "--persistence"),
    description = Array("Turn on persistence (cache)."))
  private val persistence = new Array[Boolean](0)

  import picocli.CommandLine.Parameters

  @Parameters(arity = "1", paramLabel = "swan-dir", description = Array("swan-dir to process."))
  private val inputFile: File = null

  var options: DefaultDriver.Options = _

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
    Logging.printInfo("Writing " + module.toString + " to " + f.getName)
    fw.write(printedSwirlModule)
    fw.close()
  }

  def runner(debugDir: File, file: File, options: DefaultDriver.Options, p: Persistence): (SILModule, Module, CanModule) = {
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    if (options.persistence && !p.createdNewCache) {
      silModule.functions.foreach(f => {
        if (!p.checkFunctionParity(f, silModule)) {
          Logging.printInfo("Detected change: " + f.name.mangled + "\n  (" + f.name.demangled + ")")
        }
      })
    }
    val swirlModule = new SWIRLGen().translateSILModule(silModule)
    if (options.printSwirl) writeFile(swirlModule, debugDir, file.getName + ".raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.printSwirl) writeFile(canSwirlModule, debugDir, file.getName)
    (silModule, swirlModule, canSwirlModule)
  }

  def modelRunner(debugDir: File, modelsContent: String, options: DefaultDriver.Options): (Module, CanModule) = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    if (options.printSwirl) writeFile(swirlModule, debugDir, "models.raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.printSwirl) writeFile(canSwirlModule, debugDir, "models")
    (swirlModule, canSwirlModule)
  }

  override def run(): Unit = {
    options = new DefaultDriver.Options()
      .printSwirl(debug.nonEmpty)
      .persistence(persistence.nonEmpty)
    runActual(options)
  }

  def runActual(options: DefaultDriver.Options, swanDir: File = inputFile): ModuleGroup = {
    val p = if (options.persistence) new Persistence(swanDir, invalidateCache.nonEmpty) else null
    if (options.persistence) Logging.printInfo(p.toString)
    val dirProcessor = new DirProcessor(swanDir.toPath)
    val silFiles = if (options.persistence) p.changedSILFiles else dirProcessor.process()
    val threads = new ArrayBuffer[Thread]()
    val silModules = new ArrayBuffer[SILModule]()
    val rawModules = new ArrayBuffer[Module]()
    val canModules = new ArrayBuffer[CanModule]()
    val debugDir = Files.createDirectories(
      Paths.get(swanDir.getPath, "debug-dir")).toFile
    FileUtils.cleanDirectory(debugDir)
    silFiles.foreach(f => {
      if (single.nonEmpty) {
        val res = runner(debugDir, f, options, p)
        silModules.append(res._1)
        rawModules.append(res._2)
        canModules.append(res._3)
      } else {
        val t = new Thread {
          override def run(): Unit = {
            val res = runner(debugDir, f, options, p)
            silModules.append(res._1)
            rawModules.append(res._2)
            canModules.append(res._3)
          }
        }
        threads.append(t)
        t.start()
      }
    })
    if (silFiles.nonEmpty) {
      // Single file for now, iterating files is tricky with JAR resources)
      val in = this.getClass.getClassLoader.getResourceAsStream("models.swirl")
      val modelsContent = IOUtils.toString(in, StandardCharsets.UTF_8)
      if (single.nonEmpty) {
        val res = modelRunner(debugDir, modelsContent, options)
        rawModules.append(res._1)
        canModules.append(res._2)
      } else {
        val t = new Thread {
          override def run(): Unit = {
            val res = modelRunner(debugDir, modelsContent, options)
            rawModules.append(res._1)
            canModules.append(res._2)
          }
        }
        threads.append(t)
        t.start()
      }
      if (!single.nonEmpty) threads.foreach(f => f.join())
      if (options.persistence) p.updateSILModules(silModules)
      if (options.persistence) p.writeCache()
      val group = ModuleGrouper.group(canModules)
      Logging.printInfo("Group ready:\n"+group.toString+group.functions.length+" functions")
      if (options.printSwirl) writeFile(group, debugDir, "grouped")
      group
    } else {
      null
    }
  }
}
