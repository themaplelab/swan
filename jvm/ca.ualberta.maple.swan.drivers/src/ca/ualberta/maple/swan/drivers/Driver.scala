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
import picocli.CommandLine.{Command, Option, Parameters}

import scala.collection.mutable.ArrayBuffer

object Driver {
  class Options {
    var debug = false
    var single = false
    var cache = false
    var silModuleCB: SILModule => Unit = _
    var rawSwirlModuleCB: Module => Unit = _
    var canSwirlModuleCB: CanModule => Unit = _
    def debug(v: Boolean): Options = {
      this.debug = v; this
    }
    def cache(v: Boolean): Options = {
      this.cache = v; this
    }
    def single(v: Boolean): Options = {
      this.single = v; this
    }
    def addSILCallBack(cb: SILModule => Unit): Options = {
      silModuleCB = cb; this
    }
    def addRawSWIRLCallBack(cb: Module => Unit): Options = {
      rawSwirlModuleCB = cb; this
    }
    def addCanSWIRLCallBack(cb: CanModule => Unit): Options = {
      canSwirlModuleCB = cb; this
    }
  }

  def main(args: Array[String]): Unit = {
    val exitCode = new CommandLine(new Driver).execute(args:_*)
    System.exit(exitCode);
  }
}

@Command(name = "SWAN Driver", mixinStandardHelpOptions = true, header = Array(
  "@|fg(208)" +
  "    WNW                                                                   WWW \n" +
  "  WOdkXW                                                              WNKOOXW\n" +
  "  Xd:clx0XWW                                                      WWNK0kxxx0W\n" +
  " WOl::cccoxOXNW                                               WNXK0OkxxddddON\n" +
  " WOc:::cccccldk0KNNWW                 WWWWk             WNNXK0Okxdddddxdddx0N\n" +
  " W0l:::::ccccccclodxkOO00KNW        WKkxxOXW           WXOxddooodddddddddxxkXW\n" +
  "  Xd::::::ccccccccccccclllx0N      WOlcccl0         WXkoooooooooodddddddxOXW \n" +
  "  WKo::::::ccccccccccccclllokXW    Nxo   xK       NKkolllllooooooooddxO0XW   \n" +
  "   WXxl::::::cccccccccccccllldONW   xkN        WN0dlccclllllooooooooxKW      \n" +
  "     WX0dc:::::cccccccccccccllldOXW  xOXN    WXkocccccccclllllloooookN       \n" +
  "       WKo::::::cccccccccccccllllok0  kxxxxk   WcccccccccllllllooxOXW        \n" +
  "        WOo::::::ccccccccccclccllllod   XKOdlk   WkcccccccloddkOKNW          \n" +
  "         WXOdlc:::cccccccccccccclldxOKN   WNklck  Wkccccd0XNWW               \n" +
  "            WX0OkkOkdlcccccccccldOXW       W0lclk  WccloxOXW                 \n" +
  "                   WN0xdolllloodOX        dWXKKXN                            \n" +
  "                      WWNXKKKXNWW  WX0OkxxddxkKN                             \n" +
  "                                      WNNNNWW                                 |@"))
class Driver extends Runnable {

  @Option(names = Array("-s", "--single"),
    description = Array("Use a single thread."))
  private val singleThreaded = new Array[Boolean](0)

  @Option(names = Array("-d", "--debug"),
    description = Array("Dump IRs and changed partial files to debug directory."))
  private val debugPrinting = new Array[Boolean](0)

  @Option(names = Array("-i", "--invalidate-cache"),
    description = Array("Invalidate cache."))
  private val invalidateCache = new Array[Boolean](0)

  @Option(names = Array("-c", "--cache"),
    description = Array("Cache SIL and SWIRL group module. This is experimental, slow, and incomplete (DDGs and CFGs not serialized)."))
  private val useCache = new Array[Boolean](0)

  @Parameters(arity = "1", paramLabel = "swan-dir", description = Array("swan-dir to process."))
  private val inputFile: File = null

  var options: Driver.Options = _

  override def run(): Unit = {
    options = new Driver.Options()
      .debug(debugPrinting.nonEmpty)
      .cache(useCache.nonEmpty)
      .single(singleThreaded.nonEmpty)
    runActual(options, inputFile)
  }

  // Can return null
  def runActual(options: Driver.Options, swanDir: File): ModuleGroup = {
    val runStartTime = System.nanoTime()
    val proc = new SwanDirProcessor(swanDir, options, invalidateCache.nonEmpty)
    val treatRegular = !options.cache || invalidateCache.nonEmpty || !proc.hadExistingCache
    if (!treatRegular) Logging.printInfo(proc.toString)
    if (proc.files.isEmpty || (options.cache && !proc.changeDetected)) return null
    val debugDir: File = {
      if (options.debug) {
        val dd = Files.createDirectories(
          Paths.get(swanDir.getPath, "debug-dir")).toFile
        FileUtils.cleanDirectory(dd)
        dd
      } else {
        null
      }
    }
    val threads = new ArrayBuffer[Thread]()
    val silModules = new ArrayBuffer[SILModule]()
    val rawModules = new ArrayBuffer[Module]()
    val canModules = new ArrayBuffer[CanModule]()
    // Large files go first so we can immediately thread them

    proc.files.sortWith(_.length() > _.length()).foreach(f => {
      def go(): Unit = {
        val res = runner(debugDir, f, options)
        silModules.append(res._1)
        rawModules.append(res._2)
        canModules.append(res._3)
      }
      // Don't bother threading for <10MB
      if (options.single || f.length() < 10485760) {
        go()
      } else {
        val t = new Thread {
          override def run(): Unit = {
            go()
          }
        }
        threads.append(t)
        t.start()
      }
    })
    if (treatRegular) {
      // Single file for now, iterating files is tricky with JAR resources)
      val in = this.getClass.getClassLoader.getResourceAsStream("models.swirl")
      val modelsContent = IOUtils.toString(in, StandardCharsets.UTF_8)
      val res = modelRunner(debugDir, modelsContent, options)
      rawModules.append(res._1)
      canModules.append(res._2)
    }
    threads.foreach(t => t.join())
    val group = {
      if (treatRegular) {
        ModuleGrouper.group(canModules)
      } else {
        ModuleGrouper.group(canModules, proc.cachedGroup, proc.changedFiles)
      }
    }
    Logging.printTimeStampSimple(0, runStartTime, "(group ready for analysis)")
    Logging.printInfo(
      group.toString+group.functions.length+" functions\n"+
      group.entries.size+" entries")
    if (options.debug) writeFile(group, debugDir, "grouped")
    if (options.cache) proc.writeCache(group)
    group
  }

  def runner(debugDir: File, file: File, options: Driver.Options): (SILModule, Module, CanModule) = {
    val partial = file.getName.endsWith(".changed")
    val silParser = new SILParser(file.toPath)
    val silModule = silParser.parseModule()
    if (options.silModuleCB != null) options.silModuleCB(silModule)
    val rawSwirlModule = new SWIRLGen().translateSILModule(silModule)
    if (partial && rawSwirlModule.functions(0).name.startsWith("SWAN_FAKE_MAIN")) rawSwirlModule.functions.remove(0)
    if (options.rawSwirlModuleCB != null) options.rawSwirlModuleCB(rawSwirlModule)
    if (options.debug) writeFile(rawSwirlModule, debugDir, file.getName + ".raw")
    val canSwirlModule = new SWIRLPass().runPasses(rawSwirlModule)
    if (options.canSwirlModuleCB != null) options.canSwirlModuleCB(canSwirlModule)
    if (options.debug) writeFile(canSwirlModule, debugDir, file.getName)
    (silModule, rawSwirlModule, canSwirlModule)
  }

  def modelRunner(debugDir: File, modelsContent: String, options: Driver.Options): (Module, CanModule) = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    if (options.debug) writeFile(swirlModule, debugDir, "models.raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.debug) writeFile(canSwirlModule, debugDir, "models")
    (swirlModule, canSwirlModule)
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
    Logging.printInfo("Writing " + module.toString + " to " + f.getName)
    fw.write(printedSwirlModule)
    fw.close()
  }
}