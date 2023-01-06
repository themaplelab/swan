/*
 * Copyright (c) 2023 the SWAN project authors. All rights reserved.
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

import ca.ualberta.maple.swan.drivers.Driver.{cryptoAnalysisResultsFileName, taintAnalysisResultsFileName, typeStateAnalysisResultsFileName}
import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.parser.{SILModule, SILParser}
import ca.ualberta.maple.swan.spds.Stats
import ca.ualberta.maple.swan.spds.Stats.{AllStats, GeneralStats}
import ca.ualberta.maple.swan.spds.analysis.crypto.CryptoAnalysis
import ca.ualberta.maple.swan.spds.analysis.taint._
import ca.ualberta.maple.swan.spds.analysis.typestate.{TypeStateAnalysis, TypeStateResults}
import ca.ualberta.maple.swan.spds.cg.CallGraphBuilder.CallGraphStyle.Style
import ca.ualberta.maple.swan.spds.cg.{CallGraphBuilder, CallGraphConstructor, CallGraphUtils}
import ca.ualberta.maple.swan.utils.Logging
import org.apache.commons.io.{FileExistsException, FileUtils, IOUtils}
import picocli.CommandLine
import picocli.CommandLine.{ArgGroup, Command, Option, Parameters}

import java.io.{File, FileWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ExecutionException, FutureTask, TimeUnit, TimeoutException}
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

object Driver {

  val taintAnalysisResultsFileName = "taint-results.json"
  val typeStateAnalysisResultsFileName = "typestate-results.json"
  val cryptoAnalysisResultsFileName = "crypto-results.json"

  private val callGraphOptions = immutable.HashMap(
    ("chafp", CallGraphBuilder.CallGraphStyle.CHA_FP),
    ("vtafp", CallGraphBuilder.CallGraphStyle.VTA_FP),
    ("ucg", CallGraphBuilder.CallGraphStyle.UCG),
    ("ucg_no_vta", CallGraphBuilder.CallGraphStyle.UCG_NO_VTA))

  /* Because this driver can be invoked programmatically, most picocli options
   * (@Option) should have a matching field in Driver.Options.
   */
  class Options {
    var debug = false
    var single = false
    var cache = false
    var dumpFunctionNames = false
    var constructCallGraph = false
    var callGraphTimeout = 0
    var callGraphAlgorithms: ArrayBuffer[CallGraphBuilder.CallGraphStyle.Style] = ArrayBuffer.empty
    var moduleFilters: ArrayBuffer[String] = ArrayBuffer.empty
    var taintAnalysisSpec: scala.Option[File] = None
    var typeStateAnalysisSpec: scala.Option[File] = None
    var pathTracking = false
    var analyzeLibraries = false
    var analyzeClosures = false
    var analyzeCrypto = false
    var skipEntryPointsPruning = false
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
    def callGraphTimeout(v: Array[Int]): Options = {
      if (v.length == 0) {
        this.callGraphTimeout = 0
      }
      else {
        this.callGraphTimeout = v(0)
      }
      this
    }
    def callGraphPAAlgorithms(a: Array[Driver.CGPAPair]): Options = {
      a.foreach(v => {
        if (v != null) {
          callGraphOptions.get(v.cgAlgorithm.toLowerCase()) match {
            case Some(value) => callGraphAlgorithms.append(value)
            case None => throw new RuntimeException("Unrecognized CG style. Options are " + callGraphOptions.keys.mkString(", "))
          }
        }
      })
      if (this.callGraphAlgorithms.isEmpty) {
        val style = CallGraphBuilder.CallGraphStyle.UCG_NO_VTA
        this.callGraphAlgorithms.append(style)
      }
      this
    }
    def moduleFilters(a: Array[Driver.ModuleFilter]): Options = {
      a.foreach(v => {
        if (v != null) moduleFilters.append(v.module)
      })
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
    def analyzeCrypto(v: Boolean): Options = {
      this.analyzeCrypto = v; this
    }
    def analyzeClosures(v: Boolean): Options = {
      this.analyzeClosures = v; this
    }
    def skipEntryPointsPruning(v: Boolean): Options = {
      this.skipEntryPointsPruning = v; this
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

  class CGPAPair() {
    @Option(names = Array("--cg-algo"),
      required = true,
      description = Array("Algorithm(s) used for building the Call Graph.")
    )
    var cgAlgorithm: String = null
  }

  class ModuleFilter() {
    @Option(names = Array("--module"),
      required = false,
      description = Array("Modules to analyze (basically selective analysis mostly meant for libraries). Uses regex matching.")
    )
    var module: String = null
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
  "\n\n Copyright (c) 2023 the SWAN project authors. All rights reserved.\n"         +
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
    description = Array("Construct the Call Graph (allowing you to omit -t or -e)."))
  private val constructCallGraph = new Array[Boolean](0)

  @Option(names = Array("--cg-timeout-seconds"),
    description = Array("Set timeout for call graph construction"))
  private val cgTimeout: Array[Int] = Array.empty[Int]

  @ArgGroup(exclusive = false, multiplicity="0..*")
  private val callGraphAndPointerAlgorithms: Array[Driver.CGPAPair] = Array.empty[Driver.CGPAPair]

  @ArgGroup(exclusive = false, multiplicity="0..*")
  private val moduleFilters: Array[Driver.ModuleFilter] = Array.empty[Driver.ModuleFilter]

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

  @Option(names = Array("--skip-entry-point-pruning"),
    description = Array("Skips pruning entry points."))
  private val skipEntryPointsPruning = new Array[Boolean](0)

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

  @Option(names = Array("--crypto"),
    description = Array("Analyze application for crypto API misuses. Currently only supports CryptoSwift."))
  private val cryptoAnalysis = new Array[Boolean](0)

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
      .callGraphTimeout(cgTimeout)
      .callGraphPAAlgorithms(callGraphAndPointerAlgorithms)
      .moduleFilters(moduleFilters)
      .taintAnalysisSpec(taintAnalysisSpec)
      .typeStateAnalysisSpec(typeStateAnalysisSpec)
      .pathTracking(pathTracking.nonEmpty)
      .analyzeLibraries(analyzeLibraries.nonEmpty)
      .analyzeCrypto(cryptoAnalysis.nonEmpty)
      .analyzeClosures(analyzeClosures.nonEmpty)
      .skipEntryPointsPruning(skipEntryPointsPruning.nonEmpty)
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
    Logging.printInfo(group.toString+group.functions.length+" functions")
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
    val stats = new mutable.HashMap[String, AllStats]()
    if (options.constructCallGraph || options.taintAnalysisSpec.nonEmpty || options.typeStateAnalysisSpec.nonEmpty || options.analyzeCrypto) {
      options.callGraphAlgorithms.foreach { cgAlgo =>
        if (options.callGraphTimeout == 0) {
          val cgResults = createCallGraph(group, cgAlgo)
          manageCGResults(options, swanDir, generalStats, debugDir, stats, cgAlgo, cgResults)
        }
        else {
          createCallGraphOrTimeout(group, cgAlgo, options.callGraphTimeout) match {
            case Some(cgResults) =>
            manageCGResults(options, swanDir, generalStats, debugDir, stats, cgAlgo, cgResults)
            case None =>
              Logging.printInfo("WARNING: CallGraph " + cgAlgoPrefix(cgAlgo) + " timed out.")
          }
        }
      }
    }
    generalStats.modules = group.metas.length - 1
    generalStats.functions = group.functions.length
    generalStats.totalRunTimeMS = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - runStartTime).toInt
    stats.foreach(s => {
      val prefix = s._1
      System.out.println(s._2)
      val allStatsFile = Paths.get(swanDir.getPath, s"$prefix-stats.json").toFile
      Logging.printInfo(s"Writing $prefix stats to ${allStatsFile.getName}")
      s._2.writeToFile(allStatsFile)
    })
    if (options.callGraphAlgorithms.length > 1) {
      Logging.printInfo("NOTE: Multiple CGs generated - total runtime will be longer.")
    }
    group
  }

  /** Computes a call graph */
  private def createCallGraph(group: ModuleGroup, cgAlgo: Style): Stats.CallGraphStats = {
    CallGraphBuilder.createCallGraph(
      group, cgAlgo,
      new CallGraphConstructor.Options(analyzeLibraries = options.analyzeLibraries,
      analyzeClosures = options.analyzeClosures,
      skipEntryPointsPruning = options.skipEntryPointsPruning,
      addDebugInfo = options.debug))
  }


  private def manageCGResults(options: Driver.Options, swanDir: File, generalStats: GeneralStats, debugDir: File, stats: mutable.HashMap[String, AllStats], cgAlgo: Style, cgResults: Stats.CallGraphStats): scala.Option[AllStats] = {
    val allStats = new AllStats(generalStats)
    allStats.cgs = Some(cgResults)
    val cg = cgResults.cg
    val cgPrefix = cgAlgoPrefix(cgAlgo)
    if (options.printToDot) {
      val fw = new FileWriter(Paths.get(swanDir.getPath, s"$cgPrefix-dot.txt").toFile)
      try {
        fw.write(cg.toDot)
      } finally {
        fw.close()
      }
    }
    if (options.printToProbe) {
      CallGraphUtils.writeToProbe(cg, Paths.get(swanDir.getPath, s"$cgPrefix.probe.txt").toFile)
      CallGraphUtils.writeToProbe(cg, Paths.get(swanDir.getPath, s"$cgPrefix.insensitive.probe.txt").toFile, contextSensitive = false)
    }
    if (options.debug) {
      writeFile(cgResults.finalModuleGroup, debugDir, s"$cgPrefix-grouped-cg", new SWIRLPrinterOptions().cgDebugInfo(cgResults.debugInfo).printLocation(true).printLocationPaths(false))
      if (cgResults.dynamicModels.nonEmpty) {
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
        val analysis = new TaintAnalysis(spec, analysisOptions, debugDir)
        val results = analysis.run(cg)
        Logging.printInfo(results.toString)
        allResults.append(results)
      })
      val f = Paths.get(swanDir.getPath, taintAnalysisResultsFileName).toFile
      TaintSpecification.writeResults(f, allResults)
      Logging.printInfo("Taint results written to " + taintAnalysisResultsFileName)
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
      Logging.printInfo("Typestate results written to " + typeStateAnalysisResultsFileName)
    }
    if (options.analyzeCrypto) {
      val results = new CryptoAnalysis(cg, debugDir, analyzeLibraries.nonEmpty).evaluate()
      val f = Paths.get(swanDir.getPath, cryptoAnalysisResultsFileName).toFile
      results.writeResults(f)
      Logging.printInfo("Crypto results written to " + cryptoAnalysisResultsFileName)
    }
    stats.put(cgPrefix, allStats)
  }

  private def cgAlgoPrefix(cgAlgo: Style) = {
    cgAlgo match {
      case CallGraphBuilder.CallGraphStyle.CHA_FP => "CHA_FP"
      case CallGraphBuilder.CallGraphStyle.VTA_FP => "VTA_FP"
      case CallGraphBuilder.CallGraphStyle.UCG => "UCG"
      case CallGraphBuilder.CallGraphStyle.UCG_NO_VTA => "UCG_NO_VTA"
//      case CallGraphBuilder.CallGraphStyle.CHA => "CHA"
//      case CallGraphBuilder.CallGraphStyle.CHA_SIGMATCHING => "CHA_SIG"
//      case CallGraphBuilder.CallGraphStyle.ORTA => "ORTA"
//      case CallGraphBuilder.CallGraphStyle.ORTA_SIGMATCHING => "ORTA_SIG"
//      case CallGraphBuilder.CallGraphStyle.PRTA => "PRTA"
//      case CallGraphBuilder.CallGraphStyle.PRTA_SIGMATCHING => "PRTA_SIG"
//      case CallGraphBuilder.CallGraphStyle.SPDS => "SPDS"
//      case CallGraphBuilder.CallGraphStyle.SPDS_WP_FILTER => "SPDS_WPF"
//      case CallGraphBuilder.CallGraphStyle.SPDS_QUERY_FILTER => "SPDS_QUERYF"
//      case CallGraphBuilder.CallGraphStyle.VTA => "VTA"
//      case CallGraphBuilder.CallGraphStyle.UCG => "UCG"
//      case CallGraphBuilder.CallGraphStyle.UCG_VTA => "UCG_VTA"
//      case CallGraphBuilder.CallGraphStyle.UCG_SPDS => "UCG_SPDS"
//      case CallGraphBuilder.CallGraphStyle.UCG_SPDS_DYNAMIC => "UCG_SPDS_DYNAMIC"
//      case CallGraphBuilder.CallGraphStyle.UCG_VTA_SPDS => "UCG_VTA_SPDS"
    }
  }

  private def createCallGraphOrTimeout(group: ModuleGroup, cgAlgo: Style, withTimeoutSeconds: Int): scala.Option[Stats.CallGraphStats] = {
    val jft = new FutureTask[Stats.CallGraphStats](() => createCallGraph(group, cgAlgo))
    try {
      val t = new Thread(jft, "cgThread")
      t.start()
      val cg = jft.get(withTimeoutSeconds, TimeUnit.SECONDS)
      Some(cg)
    } catch {
      case e: ExecutionException =>
        throw e.getCause
      case e: TimeoutException =>
        jft.cancel(true)
        None
    }
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
    val canSwirlModule = new SWIRLPass().runPasses(rawSwirlModule, options.moduleFilters)
    if (options.canSwirlModuleCB != null) options.canSwirlModuleCB(canSwirlModule)
    if (options.debug) writeFile(canSwirlModule, debugDir, file.getName)
    (silModule, rawSwirlModule, canSwirlModule)
  }

  /** Processes a SWIRL file. */
  def swirlRunner(debugDir: File, modelsContent: String, options: Driver.Options, name: String): (Module, CanModule) = {
    val swirlModule = new SWIRLParser(modelsContent, model = true).parseModule()
    if (options.debug) writeFile(swirlModule, debugDir, name + ".raw")
    val canSwirlModule = new SWIRLPass().runPasses(swirlModule, options.moduleFilters)
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