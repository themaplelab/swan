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

package ca.ualberta.maple.swan.drivers

import ca.ualberta.maple.swan.drivers.Driver.{taintAnalysisResultsFileName, typeStateAnalysisResultsFileName}
import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.parser.{SILModule, SILParser}
import ca.ualberta.maple.swan.spds.Stats.{AllStats, GeneralStats}
import ca.ualberta.maple.swan.spds.analysis.taint._
import ca.ualberta.maple.swan.spds.analysis.typestate.{TypeStateAnalysis, TypeStateResults}
import ca.ualberta.maple.swan.spds.cg.{CallGraphBuilder, CallGraphConstructor, CallGraphUtils}
import ca.ualberta.maple.swan.utils.Logging
import org.apache.commons.io.{FileExistsException, FileUtils, IOUtils}
import picocli.CommandLine
import picocli.CommandLine.{Command, Option, Parameters}

import java.io.{File, FileWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer

object Driver {

  val taintAnalysisResultsFileName = "taint-results.json"
  val typeStateAnalysisResultsFileName = "typestate-results.json"

  /* Because this driver can be invoked programmatically, most picocli options
   * (@Option) should have a matching field in Driver.Options.
   */
  class Options {
    var debug = false
    var single = false
    var cache = false
    var dumpFunctionNames = false
    var constructCallGraph = false
    var callGraphAlgorithm: CallGraphBuilder.CallGraphStyle.Style = CallGraphBuilder.CallGraphStyle.UCGSound
    var pointerAnalysisAlgorithm: CallGraphBuilder.PointerAnalysisStyle.Style = null
    var taintAnalysisSpec: scala.Option[File] = None
    var typeStateAnalysisSpec: scala.Option[File] = None
    var pathTracking = false
    var analyzeLibraries = false
    var analyzeClosures = false
    var printToProbe = false
    var printToDot = false
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
    def dumpFunctionNames(v: Boolean): Options = {
      this.dumpFunctionNames = v; this
    }
    def constructCallGraph(v: Boolean): Options = {
      this.constructCallGraph = v; this
    }
    def callGraphAlgorithm(v: String): Options = {
      if (v != null) {
        val style = v.toLowerCase() match {
          case "cha" => CallGraphBuilder.CallGraphStyle.CHA
          case "prta" => CallGraphBuilder.CallGraphStyle.PRTA
          case "srta" => CallGraphBuilder.CallGraphStyle.SRTA
          case "vta" => CallGraphBuilder.CallGraphStyle.VTA
          case "ucg" => CallGraphBuilder.CallGraphStyle.UCGSound
          case "unsound_ucg" => CallGraphBuilder.CallGraphStyle.UCG
        }
        this.callGraphAlgorithm = style
      }
      this
    }
    def pointerAnalysisAlgorithm(v: String): Options = {
      if (v != null) {
        val style = v.toLowerCase() match {
          case "spds" => CallGraphBuilder.PointerAnalysisStyle.SPDS
          case "uff" => CallGraphBuilder.PointerAnalysisStyle.UFF
          case "none" => CallGraphBuilder.PointerAnalysisStyle.None
          case _ => throw new RuntimeException("Unknown pointer analysis style")
        }
        this.pointerAnalysisAlgorithm = style
      }
      this
    }
    def taintAnalysisSpec(v: File): Options = {
      this.taintAnalysisSpec = scala.Option(v); this
    }
    def typeStateAnalysisSpec(v: File): Options = {
      this.typeStateAnalysisSpec = scala.Option(v); this
    }
    def pathTracking(v: Boolean): Options = {
      this.pathTracking = v; this
    }
    def analyzeLibraries(v: Boolean): Options = {
      this.analyzeLibraries = v; this
    }
    def analyzeClosures(v: Boolean): Options = {
      this.analyzeClosures = v; this
    }
    def printToProbe(v: Boolean): Options = {
      this.printToProbe = v; this
    }
    def printToDot(v: Boolean): Options = {
      this.printToDot = v; this
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

/**
 * This is the main driver for SWAN. The driver can either be invoked from the
 * command line or programmatically (e.g., with a test driver) using runActual().
 */
@Command(name = "SWAN Driver", mixinStandardHelpOptions = true, header = Array(
  "@|fg(208)" +
  "    WNW                                                                   WWW \n" +
  "  WOdkXW                                                              WNKOOOXW\n" +
  "  Xd:clx0XWW                                                      WWNK0kxxxx0WW\n"+
  " WOl::cccoxOXNW                                               WNXK0OkxxdddddONW\n"+
  " WOc:::cccccldk0KNNWW                 WWWWk             WNNXK0Okxdddddxddddx0NW\n"+
  " W0l:::::ccccccclodxkOO00KNW        WKkxxOXW           WXOxddooodddddddddxxkXW\n" +
  "  Xd::::::ccccccccccccclllx0N      WOlcccl0         WXkoooooooooodddddddxOXW \n"  +
  "  WKo::::::ccccccccccccclllokXW    Nxo   xK       NKkolllllooooooooddxO0XW   \n"  +
  "   WXxl::::::cccccccccccccllldONW   xkN        WN0dlccclllllooooooooxKW      \n"  +
  "     WX0dc:::::cccccccccccccllldOXW  xOXN    WXkocccccccclllllloooookN       \n"  +
  "       WKo::::::cccccccccccccllllok0  kxxxxk   WcccccccccllllllooxOXW        \n"  +
  "        WOo::::::ccccccccccclccllllod   XKOdlk   WkcccccccloddkOKNW          \n"  +
  "         WXOdlc:::cccccccccccccclldxOKN   WNklck  Wkccccd0XNWW               \n"  +
  "            WX0OkkOkdlcccccccccldOXW       W0lclk  WccloxOXW                 \n"  +
  "                   WN0xdolllloodOX        dWXKKXN                            \n"  +
  "                      WWNXKKKXNWW  WX0OkxxddxkKN                             \n"  +
  "                                      WNNNNWW                                 |@" +
  "\n\n Copyright (c) 2021 the SWAN project authors. All rights reserved.\n"         +
  " Licensed under the Apache License, Version 2.0, available at\n"                  +
  " http://www.apache.org/licenses/LICENSE-2.0\n"                                    +
  " This software has dependencies with other licenses.\n"                           +
  " See https://github.com/themaplelab/swan/doc/LICENSE.md.\n"))
class Driver extends Runnable {

  @Option(names = Array("-s", "--single"),
    description = Array("Use a single thread."))
  private val singleThreaded = new Array[Boolean](0)

  @Option(names = Array("-d", "--debug"),
    description = Array("Dump IRs and changed partial files to debug directory."))
  private val debugPrinting = new Array[Boolean](0)

  @Option(names = Array("-n", "--names"),
    description = Array("Dump functions names to file in debug directory (e.g., for finding sources/sinks)."))
  private val dumpFunctionNames = new Array[Boolean](0)

  @Option(names = Array("-g", "--call-graph"),
    description = Array("Construct the Call Graph."))
  private val constructCallGraph = new Array[Boolean](0)

  @Option(names = Array("--cg-algo"),
    description = Array("Algorithm used for building the Call Graph."))
  private val callGraphAlgorithm = new Array[String](1)

  @Option(names = Array("--pa-algo"),
    description = Array("Algorithm used for pointer analysis during Call Graph construction."))
  private val pointerAnalysisAlgorithm = new Array[String](1)

  @Option(names = Array("-t", "--taint-analysis-spec"),
    description = Array("JSON specification file for taint analysis."))
  private val taintAnalysisSpec: File = null

  @Option(names = Array("-e", "--typestate-analysis-spec"),
    description = Array("JSON specification file for typestate analysis."))
  private val typeStateAnalysisSpec: File = null

  @Option(names = Array("-p", "--path-tracking"),
    description = Array("Enable path tracking for taint analysis (experimental and is known to hang)."))
  private val pathTracking = new Array[Boolean](0)

  @Option(names = Array("-i", "--invalidate-cache"),
    description = Array("Invalidate cache."))
  private val invalidateCache = new Array[Boolean](0)

  @Option(names = Array("-l", "--analyze-libraries"),
    description = Array("Analyze libraries (treat as entry points)."))
  private val analyzeLibraries = new Array[Boolean](0)

  @Option(names = Array("-u", "--analyze-closures"),
    description = Array("Analyze closures (limited support)."))
  private val analyzeClosures = new Array[Boolean](0)

  @Option(names = Array("-r", "--probe"),
    description = Array("Print probe CG to cg.txt."))
  private val printProbe = new Array[Boolean](0)

  @Option(names = Array("-o", "--dot"),
    description = Array("Print dot CG to dot.txt."))
  private val printDot = new Array[Boolean](0)

  @Option(names = Array("-f", "--force-cache-read"),
    description = Array("Force reading the cache, regardless of changed files."))
  private val forceRead = new Array[Boolean](0)

  @Option(names = Array("-c", "--cache"),
    description = Array("Cache SIL and SWIRL group module. This is experimental, slow, and incomplete (DDGs and CFGs not serialized)."))
  private val useCache = new Array[Boolean](0)

  @Parameters(arity = "1", paramLabel = "swan-dir", description = Array("swan-dir to process."))
  private val inputFile: File = null

  var options: Driver.Options = _

  /**
   * Convert picocli options to Driver.Options and call runActual.
   */
  override def run(): Unit = {
    options = new Driver.Options()
      .debug(debugPrinting.nonEmpty)
      .cache(useCache.nonEmpty)
      .single(singleThreaded.nonEmpty)
      .dumpFunctionNames(dumpFunctionNames.nonEmpty)
      .constructCallGraph(constructCallGraph.nonEmpty)
      .callGraphAlgorithm(callGraphAlgorithm(0))
      .pointerAnalysisAlgorithm(pointerAnalysisAlgorithm(0))
      .taintAnalysisSpec(taintAnalysisSpec)
      .typeStateAnalysisSpec(typeStateAnalysisSpec)
      .pathTracking(pathTracking.nonEmpty)
      .analyzeLibraries(analyzeLibraries.nonEmpty)
      .analyzeClosures(analyzeClosures.nonEmpty)
      .printToProbe(printProbe.nonEmpty)
      .printToDot(printDot.nonEmpty)
    runActual(options, inputFile)
  }

  /**
   * Processes the given swanDir (translation and analysis) and returns
   * the module group. Can return null if the given directory is empty or
   * if a cache exists and there is no change.
   */
  def runActual(options: Driver.Options, swanDir: File): ModuleGroup = {
    if (!swanDir.exists()) {
      throw new FileExistsException("swan-dir does not exist")
    }
    val runStartTime = System.nanoTime()
    val generalStats = new GeneralStats
    val proc = new SwanDirProcessor(swanDir, options, invalidateCache.nonEmpty, forceRead.nonEmpty)
    val treatRegular = !options.cache || invalidateCache.nonEmpty || !proc.hadExistingCache
    if (!treatRegular) Logging.printInfo(proc.toString)
    // Check early exit conditions
    if (proc.files.isEmpty) Logging.printInfo("No input files found!")
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
      // Single model file for now, iterating files is tricky with JAR resources)
      val in = this.getClass.getClassLoader.getResourceAsStream("models.swirl")
      val modelsContent = IOUtils.toString(in, StandardCharsets.UTF_8)
      val res = swirlRunner(debugDir, modelsContent, options, "models")
      rawModules.append(res._1)
      canModules.append(res._2)
      // Also add any .swirl files in swan-dir (for testing, mostly)
      proc.swirlFiles.foreach(f => {
        val swirlContent = IOUtils.toString(f.toURI, StandardCharsets.UTF_8)
        val res = swirlRunner(debugDir, swirlContent, options, f.getName.substring(0, f.getName.lastIndexOf(".")))
        rawModules.append(res._1)
        canModules.append(res._2)
      })
    }
    threads.foreach(t => t.join())
    val group = {
      if (treatRegular) {
        ModuleGrouper.group(canModules)
      } else {
        ModuleGrouper.group(canModules, proc.cachedGroup, proc.changedFiles)
      }
    }
    generalStats.moduleGroupReadyTimeMS = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - runStartTime).toInt
    Logging.printTimeStampSimple(0, runStartTime, "(group ready for analysis)")
    Logging.printInfo(
      group.toString+group.functions.length+" functions\n"+
      group.entries.size+" entries")
    if (options.debug) {
      writeFile(group, debugDir, "grouped")
      val f = Paths.get(debugDir.getPath, "missing-models.txt").toFile
      var nonEmpty = false
      val fw = new FileWriter(f)
      group.functions.foreach(f => {
        if (f.attribute.nonEmpty) {
          f.attribute.get match {
            case FunctionAttribute.stub => {
              val sp = new SWIRLPrinter()
              sp.print(f)
              fw.write(sp.toString + "\n")
              nonEmpty = true
            }
            case _ =>
          }
        }
      })
      fw.close()
      if (!nonEmpty) Files.delete(f.toPath)
    }
    if (options.cache) proc.writeCache(group)
    if (options.dumpFunctionNames) {
      val f = Paths.get(swanDir.getPath, "function-names.txt").toFile
      val fw = new FileWriter(f)
      group.functions.foreach(f => {
        fw.write(f.name + "\n")
      })
      fw.close()
    }
    val allStats = new AllStats(generalStats, None)
    if (options.constructCallGraph || options.taintAnalysisSpec.nonEmpty || options.typeStateAnalysisSpec.nonEmpty) {
      val cgResults = CallGraphBuilder.createCallGraph(
        group,options.callGraphAlgorithm, scala.Option(options.pointerAnalysisAlgorithm),
        new CallGraphConstructor.Options(options.analyzeLibraries, options.analyzeClosures))
      allStats.cgs = Some(cgResults)
      val cg = cgResults.cg
      if (options.printToDot) {
        val fw = new FileWriter(Paths.get(swanDir.getPath, "dot.txt").toFile)
        try {
          fw.write(cg.toDot)
        } finally {
          fw.close()
        }
      }
      if (options.printToProbe) {
        CallGraphUtils.writeToProbe(cg, Paths.get(swanDir.getPath, "cg.txt").toFile)
      }
      if (options.debug) {
        writeFile(cgResults.finalModuleGroup, debugDir, "grouped-cg", new SWIRLPrinterOptions().cgDebugInfo(cgResults.debugInfo))
        if (cgResults.dynamicModels.nonEmpty ) {
          val r = cgResults.dynamicModels.get
          writeFile(r._1, debugDir, "dynamic-models.raw")
          writeFile(r._2, debugDir, "dynamic-models")
        }
      }
      if (options.taintAnalysisSpec.nonEmpty) {
        val allResults = new ArrayBuffer[TaintResults]()
        val specs = TaintSpecification.parse(options.taintAnalysisSpec.get)
        specs.foreach(spec => {
          val analysisOptions = new TaintAnalysisOptions(
            if (options.pathTracking) AnalysisType.ForwardPathTracking
            else AnalysisType.Forward)
          val analysis = new TaintAnalysis(spec, analysisOptions)
          val results = analysis.run(cg)
          Logging.printInfo(results.toString)
          allResults.append(results)
        })
        val f = Paths.get(swanDir.getPath, taintAnalysisResultsFileName).toFile
        TaintSpecification.writeResults(f, allResults)
      }
      if (options.typeStateAnalysisSpec.nonEmpty) {
        val allResults = new ArrayBuffer[TypeStateResults]()
        val specs = TypeStateAnalysis.parse(options.typeStateAnalysisSpec.get)
        specs.foreach(spec => {
          val analysis = new TypeStateAnalysis(cg, spec.make(cg), spec)
          val results = analysis.executeAnalysis()
          Logging.printInfo(results.toString)
          allResults.append(results)
        })
        val f = Paths.get(swanDir.getPath, typeStateAnalysisResultsFileName).toFile
        TypeStateResults.writeResults(f, allResults)
      }
    }
    generalStats.modules = group.metas.length - 1
    generalStats.functions = group.functions.length
    generalStats.totalRunTimeMS = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - runStartTime).toInt
    System.out.println(allStats)
    val allStatsFile = Paths.get(swanDir.getPath, "stats.json").toFile
    Logging.printInfo(s"Writing stats to ${allStatsFile.getName}")
    allStats.writeToFile(allStatsFile)
    group
  }

  /** Processes a SIL file. */
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

  /** Processes a SWIRL file. */
  def swirlRunner(debugDir: File, modelsContent: String, options: Driver.Options, name: String): (Module, CanModule) = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    if (options.debug) writeFile(swirlModule, debugDir, name + ".raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule)
    if (options.debug) writeFile(canSwirlModule, debugDir, name)
    (swirlModule, canSwirlModule)
  }

  /** Write a module to the debug directory. */
  def writeFile(module: Object, debugDir: File, prefix: String, options: SWIRLPrinterOptions = new SWIRLPrinterOptions): Unit = {
    val printedSwirlModule = {
      module match {
        case canModule: CanModule =>
          new SWIRLPrinter().print(canModule, options)
        case rawModule: Module =>
          new SWIRLPrinter().print(rawModule, options)
        case groupModule: ModuleGroup =>
          new SWIRLPrinter().print(groupModule, options)
        case _ =>
          throw new RuntimeException("unexpected")
      }
    }
    val f = Paths.get(debugDir.getPath, prefix + ".swirl").toFile
    val fw = new FileWriter(f)
    Logging.printInfo(s"Writing ${module.toString} to ${f.getName}")
    fw.write(printedSwirlModule)
    fw.close()
  }
}