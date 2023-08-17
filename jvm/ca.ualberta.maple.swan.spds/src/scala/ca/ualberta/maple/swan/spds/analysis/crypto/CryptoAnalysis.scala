/*
 * Copyright (c) 2022 the SWAN project authors. All rights reserved.
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

package ca.ualberta.maple.swan.spds.analysis.crypto

import boomerang.BoomerangOptions.ArrayStrategy
import boomerang.results.{AbstractBoomerangResults, BackwardBoomerangResults, ForwardBoomerangResults}
import boomerang.scene.{AllocVal, ControlFlowGraph, DataFlowScope, DeclaredMethod, Method}
import boomerang.{BackwardQuery, Boomerang, BoomerangOptions, DefaultBoomerangOptions, ForwardQuery}
import ca.ualberta.maple.swan.ir.{FunctionAttribute, Literal, Position}
import ca.ualberta.maple.swan.spds.structures.{SWANCallGraph, SWANField, SWANMethod, SWANStatement, SWANVal}
import wpds.impl.Weight

import java.io.{File, FileWriter}
import java.util
import java.util.regex.Pattern
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class CryptoAnalysis(val cg: SWANCallGraph, val debugDir: File, val analyzeLibraries: Boolean) {

  // Currently only supports CryptoSwift
  // Rule 1: Do Not Use ECB Mode for Encryption
  // Rule 2: No Non-random IVs for Encryption
  // Rule 3: Do Not Use Constant Encryption Keys
  // Rule 4: Do Not Use Constant Salts for PBE
  // Rule 5: Do Not Use < 1000 Iterations for PBE
  // T0D0: Rule 6: Do Not Use Static Seeds for Random-number Generation
  // Rule 7: Do Not Use Constant Password for PBE

  private val forwardQueryCache = mutable.HashMap.empty[ForwardQuery, ForwardBoomerangResults[Weight.NoWeight]]
  private val backwardQueryCache = mutable.HashMap.empty[BackwardQuery, BackwardBoomerangResults[Weight.NoWeight]]

  private val debug = false

  private val testCaseMode: Boolean = try { sys.env("TEST_CASE_MODE").nonEmpty } catch { case _: Exception => false }

  private val validFields = Array("_value", "data")

  // Stores a call site and the argument index of interest
  class CallSiteSelector(val apply: SWANStatement.ApplyFunctionRef, val argIdx: Int) {
    def getBackwardQuery: BackwardQuery = {
      val edge = new ControlFlowGraph.Edge(apply.m.getCFG.getPredsOf(apply).iterator().next(), apply)
      val arg = apply.getInvokeExpr.getArg(argIdx)
      val query = BackwardQuery.make(edge, arg)
      query
    }
  }

  def filterTestCaseMode(s: String): String = {
    if (testCaseMode) {
      s.replace("CryptoSwift", "test")
        .replace("throws ", "")
    } else s
  }

  // Evaluate all rules
  def evaluate(): CryptoAnalysis.Results = {
    System.out.println("=== Evaluating CryptoAnalysis: All Rules")
    val results = new CryptoAnalysis.Results()
    evaluateRule1(results)
    evaluateRule2(results)
    evaluateRule3(results)
    evaluateRule4(results)
    evaluateRule5(results)
    evaluateRule7(results)
    System.out.println(s"+++ Found ${results.values.size} total violations")
    results
  }

  private def evaluateRule1(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 1 (ECB Block Mode)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithBlockMode
    if (!analyzeLibraries) potentialCallSites.filterInPlace(p => !p.apply.m.delegate.isLibrary)
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      if (comesFrom(callSite,
        Array((filterTestCaseMode("CryptoSwift.ECB.init() -> CryptoSwift.ECB"), false)), Array(0)) ||
          isEnumOfType(callSite, "#BlockMode.ECB!enumelt")) {
        reportViolation(CryptoAnalysis.ResultType.RULE_1, resultCollector, callSite.apply, "Using ECB Block Mode")
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }

  private def evaluateRule2(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 2 (Non-Random IVs)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithIVs
    if (!analyzeLibraries) potentialCallSites.filterInPlace(p => !p.apply.m.delegate.isLibrary)
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      // Do not report a violation if the value comes from a random function.
      if (!comesFrom(callSite,
        randomFunctions)) {
        reportViolation(CryptoAnalysis.ResultType.RULE_2, resultCollector, callSite.apply,
          "Non-Random IV - Use randomIV() or another random function")
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }

  private def evaluateRule3(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 3 (Constant Keys)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithKeys
    if (!analyzeLibraries) potentialCallSites.filterInPlace(p => !p.apply.m.delegate.isLibrary)
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      val result = isConstant(callSite)
      if (result._1) {
        reportViolation(CryptoAnalysis.ResultType.RULE_3, resultCollector, callSite.apply,
          "Constant Key: " + result._2.mkString("[", ",", "]"))
      }
      if (comesFrom(callSite,
        getStaticArrayInitializers)) {
        reportViolation(CryptoAnalysis.ResultType.RULE_3, resultCollector, callSite.apply, "Constant Key")
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }

  private def evaluateRule4(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 4 (Constant Salt)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithSalts
    if (!analyzeLibraries) potentialCallSites.filterInPlace(p => !p.apply.m.delegate.isLibrary)
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      val result = isConstant(callSite)
      if (result._1) {
        reportViolation(CryptoAnalysis.ResultType.RULE_4, resultCollector, callSite.apply,
          "Constant Salt: " + result._2.mkString("[", ",", "]"))
      }
      if (comesFrom(callSite,
        getStaticArrayInitializers)) {
        reportViolation(CryptoAnalysis.ResultType.RULE_4, resultCollector, callSite.apply, "Constant Salt")
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }


  private def evaluateRule5(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 5 (Low Iteration Count)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithIterations
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      val result = isConstant(callSite)
      if (result._1) {
        val violating = mutable.ArrayBuffer.empty[Int]
        result._2.foreach {
          case Literal.int(value) if value < 100000 => violating.append(value.toInt)
          case _ =>
        }
        if (violating.nonEmpty) {
          reportViolation(CryptoAnalysis.ResultType.RULE_5, resultCollector, callSite.apply,
            "Low iteration count (<100000): " + violating.mkString("[", ",", "]"))
        }
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }

  private def evaluateRule7(resultCollector: CryptoAnalysis.Results): Unit = {
    System.out.println("=== Evaluating CryptoAnalysis: Rule 7 (Constant Password)")
    val i = resultCollector.values.size
    val potentialCallSites = getCallSitesWithPassword
    if (!analyzeLibraries) potentialCallSites.filterInPlace(p => !p.apply.m.delegate.isLibrary)
    potentialCallSites.foreach(callSite => {
      if (debug) System.out.println(s"POTENTIAL CALL SITE: ${callSite.apply} (arg ${callSite.argIdx})")
      val result = isConstant(callSite)
      if (result._1) {
        reportViolation(CryptoAnalysis.ResultType.RULE_7, resultCollector, callSite.apply,
          "Constant Password: " + result._2.mkString("[", ",", "]"))
      }
      if (comesFrom(callSite,
        getStaticArrayInitializers)) {
        reportViolation(CryptoAnalysis.ResultType.RULE_7, resultCollector, callSite.apply, "Constant Password")
      }
    })
    System.out.println(s"  Found ${resultCollector.values.size - i} violations")
  }

  private def reportViolation(tpe: CryptoAnalysis.ResultType.Value, resultCollector: CryptoAnalysis.Results,
                              apply: SWANStatement.ApplyFunctionRef, message: String): Unit = {
    val sb = new StringBuilder(s"Found violation: $message")
    apply.getPosition match {
      case Some(pos) => sb.append(s"\n  at " + pos.toString)
      case None => sb.append(" location unknown")
    }
    resultCollector.add(new CryptoAnalysis.Result(tpe, apply.getPosition, message))
    // System.out.println(sb.toString())
  }

  private def removeCryptoSwiftPrefix(arr: Array[(Int, String)]): Array[(Int, String)] = {
    val a = ArrayBuffer.empty[(Int, String)]
    arr.foreach(x => {
      a.append((x._1,  filterTestCaseMode(x._2)))
    })
    a.toArray
  }

  private def getStaticArrayInitializers: Array[(String, Boolean)] = {
    Array(
      ("Swift.Array.init<A where A == A1.Element, A1: Swift.Sequence>(A1) -> [A]", false),
      ("Swift._allocateUninitializedArray<A>(Builtin.Word) -> ([A], Builtin.RawPointer)", false),
      ("Swift._finalizeUninitializedArray<A>(__owned [A]) -> [A]", false))
  }

  // TODO: Add Regex support for these functions

  private def randomFunctions: Array[(String, Boolean)] = {
    Array(
      (filterTestCaseMode("static (extension in CryptoSwift):CryptoSwift.Cryptors.randomIV(Swift.Int) -> [Swift.UInt8]"), false),
      (".*random.*", true))
  }

  private def getCallSitesWithSalts: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithSalts = Array(
      (1, "CryptoSwift.HKDF.init(password: [Swift.UInt8], salt: [Swift.UInt8]?, info: [Swift.UInt8]?, keyLength: Swift.Int?, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.HKDF"),
      (1, "CryptoSwift.PKCS5.PBKDF1.init(password: [Swift.UInt8], salt: [Swift.UInt8], variant: CryptoSwift.PKCS5.PBKDF1.Variant, iterations: Swift.Int, keyLength: Swift.Int?) throws -> CryptoSwift.PKCS5.PBKDF1"),
      (1, "CryptoSwift.PKCS5.PBKDF1.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?) throws -> CryptoSwift.PKCS5.PBKDF1"),
      (1, "CryptoSwift.PKCS5.PBKDF2.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?, variant: CryptoSwift.PKCS5.PBKDF2.Variant) throws -> CryptoSwift.PKCS5.PBKDF2"),
      (1, "CryptoSwift.PKCS5.PBKDF2.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.PKCS5.PBKDF2"),
      (1, "CryptoSwift.Scrypt.__allocating_init(password: [Swift.UInt8], salt: [Swift.UInt8], dkLen: Swift.Int, N: Swift.Int, r: Swift.Int, p: Swift.Int) throws -> CryptoSwift.Scrypt"))
    if (testCaseMode) {
      getCallSitesUsingConfig(removeCryptoSwiftPrefix(initializersWithSalts))
    } else {
      getCallSitesUsingConfig(initializersWithSalts)
    }
  }

  private def getCallSitesWithPassword: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithPassword = Array(
      (0, "CryptoSwift.HKDF.init(password: [Swift.UInt8], salt: [Swift.UInt8]?, info: [Swift.UInt8]?, keyLength: Swift.Int?, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.HKDF"),
      (0, "CryptoSwift.PKCS5.PBKDF1.init(password: [Swift.UInt8], salt: [Swift.UInt8], variant: CryptoSwift.PKCS5.PBKDF1.Variant, iterations: Swift.Int, keyLength: Swift.Int?) throws -> CryptoSwift.PKCS5.PBKDF1"),
      (0, "CryptoSwift.PKCS5.PBKDF2.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.PKCS5.PBKDF2"),
      (0, "CryptoSwift.Scrypt.__allocating_init(password: [Swift.UInt8], salt: [Swift.UInt8], dkLen: Swift.Int, N: Swift.Int, r: Swift.Int, p: Swift.Int) throws -> CryptoSwift.Scrypt"))
    if (testCaseMode) {
      getCallSitesUsingConfig(removeCryptoSwiftPrefix(initializersWithPassword))
    } else {
      getCallSitesUsingConfig(initializersWithPassword)
    }
  }

  private def getCallSitesWithIterations: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithIterations = Array(
      (3, "CryptoSwift.PKCS5.PBKDF1.init(password: [Swift.UInt8], salt: [Swift.UInt8], variant: CryptoSwift.PKCS5.PBKDF1.Variant, iterations: Swift.Int, keyLength: Swift.Int?) throws -> CryptoSwift.PKCS5.PBKDF1"),
      (2, "CryptoSwift.PKCS5.PBKDF2.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.PKCS5.PBKDF2"),
      (2, "CryptoSwift.PKCS5.PBKDF2.init(password: [Swift.UInt8], salt: [Swift.UInt8], iterations: Swift.Int, keyLength: Swift.Int?, variant: CryptoSwift.PKCS5.PBKDF2.Variant) throws -> CryptoSwift.PKCS5.PBKDF2"))
    if (testCaseMode) {
      val a = removeCryptoSwiftPrefix(initializersWithIterations)
      // a.foreach(x => System.out.println(x._2))
      getCallSitesUsingConfig(a)
    } else {
      getCallSitesUsingConfig(initializersWithIterations)
    }
  }

  private def getCallSitesWithKeys: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithKeys: Array[(Int, String)] = Array(
      (0, "CryptoSwift.AES.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode, padding: CryptoSwift.Padding) throws -> CryptoSwift.AES"),
      (0, "CryptoSwift.AES.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode) throws -> CryptoSwift.AES"),
      (0, "CryptoSwift.AES.__allocating_init(key: Swift.String, iv: Swift.String, padding: CryptoSwift.Padding) throws -> CryptoSwift.AES"),
      (0, "CryptoSwift.AES.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.AES"),
      (0, "CryptoSwift.HMAC.__allocating_init(key: [Swift.UInt8]) -> CryptoSwift.HMAC"),
      (0, "CryptoSwift.HMAC.__allocating_init(key: Swift.String) throws -> CryptoSwift.HMAC"),
      (0, "CryptoSwift.HMAC.__allocating_init(key: [Swift.UInt8], variant: CryptoSwift.HMAC.Variant) -> CryptoSwift.HMAC"),
      (0, "CryptoSwift.HMAC.__allocating_init(key: Swift.String, variant: CryptoSwift.HMAC.Variant) throws -> CryptoSwift.HMAC"),
      (0, "CryptoSwift.ChaCha20.__allocating_init(key: [Swift.UInt8], iv: [Swift.UInt8]) throws -> CryptoSwift.ChaCha20"),
      (0, "CryptoSwift.ChaCha20.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.ChaCha20"),
      (0, "CryptoSwift.CBCMAC.__allocating_init(key: [Swift.UInt8]) throws -> CryptoSwift.CBCMAC"),
      (0, "CryptoSwift.CMAC.__allocating_init(key: [Swift.UInt8]) throws -> CryptoSwift.CMAC"),
      (0, "CryptoSwift.Poly1305.__allocating_init(key: [Swift.UInt8]) -> CryptoSwift.Poly1305"),
      (0, "CryptoSwift.Blowfish.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode, padding: CryptoSwift.Padding) throws -> CryptoSwift.Blowfish"),
      (0, "CryptoSwift.Blowfish.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode) throws -> CryptoSwift.Blowfish"),
      (0, "CryptoSwift.Blowfish.__allocating_init(key: Swift.String, iv: Swift.String, padding: CryptoSwift.Padding) throws -> CryptoSwift.Blowfish"),
      (0, "CryptoSwift.Blowfish.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.Blowfish"),
      (0, "CryptoSwift.Blowfish.__allocating_init(key: [Swift.UInt8], padding: CryptoSwift.Padding) throws -> CryptoSwift.Blowfish"),
      (0, "CryptoSwift.Rabbit.__allocating_init(key: [Swift.UInt8], iv: [Swift.UInt8]?) throws -> CryptoSwift.Rabbit"),
      (0, "CryptoSwift.Rabbit.__allocating_init(key: Swift.String) throws -> CryptoSwift.Rabbit"),
      (0, "CryptoSwift.Rabbit.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.Rabbit"),
      (0, "CryptoSwift.Rabbit.__allocating_init(key: [Swift.UInt8]) throws -> CryptoSwift.Rabbit"))
    if (testCaseMode) {
      getCallSitesUsingConfig(removeCryptoSwiftPrefix(initializersWithKeys))
    } else {
      getCallSitesUsingConfig(initializersWithKeys)
    }
  }

  private def getCallSitesWithBlockMode: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithBlockMode: Array[(Int, String)] = Array(
      (1, "CryptoSwift.AES.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode, padding: CryptoSwift.Padding) throws -> CryptoSwift.AES"),
      (1, "CryptoSwift.AES.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode) throws -> CryptoSwift.AES"),
      (1, "CryptoSwift.Blowfish.__allocating_init(key: [Swift.UInt8], blockMode: CryptoSwift.BlockMode, padding: CryptoSwift.Padding) throws -> CryptoSwift.Blowfish"))
    if (testCaseMode) {
      getCallSitesUsingConfig(removeCryptoSwiftPrefix(initializersWithBlockMode))
    } else {
      getCallSitesUsingConfig(initializersWithBlockMode)
    }
  }

  private def getCallSitesWithIVs: mutable.ArrayBuffer[CallSiteSelector] = {
    val initializersWithIVs: Array[(Int, String)] = Array(
      // Cryptors
      (1, "CryptoSwift.AES.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.AES"),
      (1, "CryptoSwift.AES.__allocating_init(key: Swift.String, iv: Swift.String, padding: CryptoSwift.Padding) throws -> CryptoSwift.AES"),
      (1, "CryptoSwift.ChaCha20.__allocating_init(key: [Swift.UInt8], iv: [Swift.UInt8]) throws -> CryptoSwift.ChaCha20"),
      (1, "CryptoSwift.ChaCha20.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.ChaCha20"),
      (1, "CryptoSwift.Blowfish.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.Blowfish"),
      (1, "CryptoSwift.Blowfish.__allocating_init(key: Swift.String, iv: Swift.String, padding: CryptoSwift.Padding) throws -> CryptoSwift.Blowfish"),
      (1, "CryptoSwift.Rabbit.__allocating_init(key: [Swift.UInt8], iv: [Swift.UInt8]?) throws -> CryptoSwift.Rabbit"),
      (1, "CryptoSwift.Rabbit.__allocating_init(key: Swift.String, iv: Swift.String) throws -> CryptoSwift.Rabbit"),
      // Block Modes
      (0, "CryptoSwift.PCBC.init(iv: [Swift.UInt8]) -> CryptoSwift.PCBC"),
      (0, "CryptoSwift.CBC.init(iv: [Swift.UInt8]) -> CryptoSwift.CBC"),
      (0, "CryptoSwift.CFB.init(iv: [Swift.UInt8], segmentSize: CryptoSwift.CFB.SegmentSize) -> CryptoSwift.CFB"),
      (0, "CryptoSwift.CCM.init(iv: [Swift.UInt8], tagLength: Swift.Int, messageLength: Swift.Int, additionalAuthenticatedData: [Swift.UInt8]?) -> CryptoSwift.CCM"),
      (0, "CryptoSwift.CCM.init(iv: [Swift.UInt8], tagLength: Swift.Int, messageLength: Swift.Int, authenticationTag: [Swift.UInt8], additionalAuthenticatedData: [Swift.UInt8]?) -> CryptoSwift.CCM"),
      (0, "CryptoSwift.OFB.init(iv: [Swift.UInt8]) -> CryptoSwift.OFB"),
      (0, "CryptoSwift.CTR.init(iv: [Swift.UInt8], counter: Swift.Int) -> CryptoSwift.CTR"),
      (0, "CryptoSwift.GCM.__allocating_init(iv: [Swift.UInt8], authenticationTag: [Swift.UInt8], additionalAuthenticatedData: [Swift.UInt8]?, mode: CryptoSwift.GCM.Mode) -> CryptoSwift.GCM"),
      (0, "CryptoSwift.GCM.__allocating_init(iv: [Swift.UInt8], additionalAuthenticatedData: [Swift.UInt8]?, tagLength: Swift.Int, mode: CryptoSwift.GCM.Mode) -> CryptoSwift.GCM"))
    if (testCaseMode) {
      getCallSitesUsingConfig(removeCryptoSwiftPrefix(initializersWithIVs))
    } else {
      getCallSitesUsingConfig(initializersWithIVs)
    }
  }

  /**
   * Given a function config, return any call sites matching it.
   * @param config An array containing the config. This is a tuple (A, B) where A is an Int
   *               representing the which argument of the function we are interested in,
   *               and B is a String representing the full name of the function.
   * @return Call sites matching the config.
   */
  private def getCallSitesUsingConfig(config: Array[(Int, String)]): mutable.ArrayBuffer[CallSiteSelector] = {
    val callSites = mutable.ArrayBuffer.empty[CallSiteSelector]
    config.foreach(init => {
      val m = cg.methods.get(init._2)
      m match {
        case Some(value) => cg.edgesInto(value).forEach(edge => {
          callSites.append(new CallSiteSelector(edge.src().asInstanceOf[SWANStatement.ApplyFunctionRef], init._1))
        })
        case None =>
      }
    })
    callSites
  }

  /**
   * Determine if the allocation sites of a query are constant.
   * This includes checking the validFields of the allocation sites
   * in case they are pointers or enums because SIL uses those for
   * literals.
   */
  private def isConstant(query: BackwardQuery): (Boolean, mutable.HashSet[Literal]) = {
    if (debug) System.out.println("IS_CONSTANT: " + query)
    val results = doBackwardQuery(query)
    var foundConstant = false
    val constantValues = mutable.HashSet.empty[Literal]
    val fieldResults = mutable.ArrayBuffer.empty[BackwardBoomerangResults[Weight.NoWeight]]
    def handleResults(r: BackwardBoomerangResults[Weight.NoWeight], ignoreFields: Boolean): Unit = {
      r.getAllocationSites.forEach((forwardQuery, _) => {
        if (!ignoreFields) findUnderlyingValuesOfField(r, forwardQuery, fieldResults)
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case c: SWANVal.Constant => {
            foundConstant = true
            constantValues.addOne(c.literal)
          }
          case _ =>
        }
      })
    }
    handleResults(results, ignoreFields = false)
    fieldResults.foreach(r => handleResults(r, ignoreFields = true))
    (foundConstant, constantValues)
  }

  private def isConstant(callSiteSelector: CallSiteSelector): (Boolean, mutable.HashSet[Literal]) = {
    var (foundConstant, constantValues) = isConstant(callSiteSelector.getBackwardQuery)
    val (a, b) = isConstantViaSingletons(callSiteSelector)
    foundConstant = foundConstant || a
    constantValues.addAll(b)
    (foundConstant, constantValues)
   }

  /**
   * This function checks if a call site argument is a particular enum type.
   * @param callSiteSelector the call site from which we are querying
   */
  private def isEnumOfType(callSiteSelector: CallSiteSelector, enm: String): Boolean = {
    // Note this function will likely break if the translation of enum is changed.
    // This relies on the type of the enum being the next (literal) statement after
    // the allocationPKCS5.PBKDF2 for the enum.
    val query = callSiteSelector.getBackwardQuery
    if (debug) System.out.println("IS_ENUM_OF_TYPE: " + query)
    val results = doBackwardQuery(query)
    var is = false
    results.getAllocationSites.forEach((forwardQuery, _) => {
      val stmt = forwardQuery.cfgEdge().getStart
      val nextStmt = forwardQuery.cfgEdge().getMethod.asInstanceOf[SWANMethod].getCFG.getSuccsOf(stmt).iterator().next()
      nextStmt.asInstanceOf[SWANStatement] match {
        case l: SWANStatement.Literal => {
          l.inst.literal match {
            case Literal.string(value) => is = is || value.matches(enm)
            case _ =>
          }
        }
        case _ =>
      }
    })
    is
  }

  /***
   * This function checks if a call site argument comes from a particular set of functions
   * @param callSiteSelector The call site from which we are querying
   * @param from The functions from which we are checking if callSiteSelector comes from.
   *             It is an array of a tuple (A, B), where A is the function name and B is
   *             whether A is a regex.
   */
  private def comesFrom(callSiteSelector: CallSiteSelector, from: Array[(String, Boolean)], check_args: Array[Int] = Array.empty): Boolean = {
    val query = callSiteSelector.getBackwardQuery
    if (debug) System.out.println("COME_FROM: " + query)

    // The class field "validFields" are fields of builtin types that can
    // propagate data implicitly. In this analysis, we consider a pointer
    // containing a value X (at field  _value) and the value X itself to
    // be the same. Sometimes a function will take the raw value (X), and
    // sometimes it will take the pointer.
    // We basically want to bridge the dataflow so that we have continuous
    // dataflow for something like this:
    //     x = X()
    //     *y = x
    //     sink(y) // detect that y itself (not the underlying value) comes from x
    // The same goes for enums, which use the "data" field for storing a value.

    // Have we found that the query comes from any of the functions in "from"?
    var found = false
    // For each from method, do a forward query from any return values from
    // that method. Then, see if that value reaches the callSiteSelector.
    from.foreach(s => {
      val methods = if (s._2) cg.methods.filter(p => Pattern.matches(s._1, p._1)) else cg.methods.filter(p => p._1.equals(s._1))
      methods.foreach(m => {
        cg.edgesInto(m._2).forEach(cgEdge => {
          val apply = cgEdge.src().asInstanceOf[SWANStatement.ApplyFunctionRef]
          val cfgEdge = new ControlFlowGraph.Edge(apply.m.getCFG.getPredsOf(apply).iterator().next(), apply)
          val queries = ArrayBuffer.empty[ForwardQuery]
          if (check_args.nonEmpty) {
            check_args.foreach(arg => {
              queries.append(new ForwardQuery(cfgEdge, new AllocVal(apply.invokeExpr.getArg(arg), apply, apply.invokeExpr.getArg(arg))))
            })
          }
          queries.append(new ForwardQuery(cfgEdge, new AllocVal(apply.getLeftOp, apply, apply.getLeftOp)))
          queries.foreach(forwardQuery => {
            val results = doForwardQuery(forwardQuery)
            results.asStatementValWeightTable().cellSet().forEach(cell => {
              // Check if we reach the callSiteSelector
              cell.getRowKey.getStart match {
                case apply: SWANStatement.ApplyFunctionRef => {
                  if (apply.equals(query.cfgEdge().getTarget) && apply.getInvokeExpr.getArg(callSiteSelector.argIdx).equals(cell.getColumnKey)) {
                    found = true
                  }
                }
                // If we come across a field write, see if that field write is one of our validFields,
                // and create a new query from the value being written to if it is.
                // Note that we only go one level deep because we use this only for when the sink
                // (or callSiteSelector) takes in a pointer or enum instead of the value itself.
                // This could also be extended to be as many levels deep as necessary to propagate
                // taintedness, for instance.
                // NOTE:
                // Only do this if the field write method is the same as the call site's method
                // to preserve context sensitivity (avoids FPs but possibly adds FNs).
                case fieldWrite: SWANStatement.FieldWrite => {
                  if (fieldWrite.uses(cell.getColumnKey) && validFields.contains(fieldWrite.getWrittenField.asInstanceOf[SWANField].name) && fieldWrite.m.equals(callSiteSelector.apply.m)) {
                    val v = fieldWrite.getFieldStore.getX
                    // Create new query for the field write object
                    val forwardQuery = new ForwardQuery(cell.getRowKey, new AllocVal(v, fieldWrite, v))
                    val results = doForwardQuery(forwardQuery)
                    results.asStatementValWeightTable().cellSet().forEach(cell => {
                      cell.getRowKey.getStart match {
                        case apply: SWANStatement.ApplyFunctionRef => {
                          // Check if we reach the callSiteSelector via this field write
                          if (apply.equals(query.cfgEdge().getTarget) && apply.getInvokeExpr.getArg(callSiteSelector.argIdx).equals(cell.getColumnKey)) {
                            if (debug) System.out.println("FOUND WITH FIELD WRITE: " + fieldWrite)
                            found = true
                          }
                        } case _ =>
                      }
                    })
                  }
                }
                case _ =>
              }
            })
          })
        })
      })
    })
    if (debug) if (found) System.out.println("FOUND") else System.out.println("NOT FOUND")
    found
  }

  /**
   * Check the query results to see if there is a constant value, including in the validFields.
   */
  private def findUnderlyingValuesOfField(initialResults: BackwardBoomerangResults[Weight.NoWeight],
                                  forwardQuery: ForwardQuery, resultCollector: mutable.ArrayBuffer[BackwardBoomerangResults[Weight.NoWeight]]): Unit = {
    initialResults.asStatementValWeightTable(forwardQuery).cellSet().forEach(cell => {
      val startStmt = cell.getRowKey.getStart
      if (startStmt.isFieldStore && startStmt.uses(cell.getColumnKey) && validFields.contains(startStmt.getWrittenField.asInstanceOf[SWANField].name)) {
        val query = BackwardQuery.make(cell.getRowKey, startStmt.getRightOp)
        val results = doBackwardQuery(query)
        resultCollector.append(results)
      }
    })
  }

  private def doBackwardQuery(query: BackwardQuery, timeout: Int = 20000): BackwardBoomerangResults[Weight.NoWeight] = {
    val boomerang = getBoomerang(timeout, getBackwardScope)
    val startTime = System.currentTimeMillis()
    val results = if (backwardQueryCache.contains(query)) backwardQueryCache(query) else boomerang.solve(query)
    backwardQueryCache.put(query, results)
    // System.out.println(s"query took ${System.currentTimeMillis() - startTime} ms")
    results
  }

  // This is just to simply get around the fact that SPDS static fields don't work
  // This is basically an analysis that tracks down possible static field allocs
  private def isConstantViaSingletons(callSiteSelector: CallSiteSelector, timeout: Int = 20000): (Boolean, mutable.HashSet[Literal]) = {
    var foundConstant = false
    val constantValues = mutable.HashSet.empty[Literal]
    val m = callSiteSelector.apply.m
    val singletonReads = getAllSingletonReads(m)
    // Include possible static field getters
    cg.outEdgeTargets(m).foreach(y => singletonReads.addAll(getAllSingletonReads(y)))
    val found = mutable.HashSet.empty[SWANStatement.StaticFieldLoad]
    singletonReads.foreach(sr => {
      val edge = new ControlFlowGraph.Edge(sr, sr.m.getCFG.getSuccsOf(sr).iterator().next())
      val q = new ForwardQuery(edge, new AllocVal(sr.getLeftOp, sr, sr.getLeftOp))
      val results = if (forwardQueryCache.contains(q)) forwardQueryCache(q) else doForwardQuery(q)
      results.asStatementValWeightTable().cellSet().forEach(cell => {
        cell.getRowKey.getStart match {
          case apply: SWANStatement.ApplyFunctionRef => {
            if ((callSiteSelector.argIdx < apply.getInvokeExpr.getArgs.size()) && apply.getInvokeExpr.getArg(callSiteSelector.argIdx).equals(cell.getColumnKey)) {
              found.add(sr)
            }
          }
          case fieldLoad: SWANStatement.FieldLoad => {
            if (fieldLoad.uses(cell.getColumnKey) && fieldLoad.m.equals(callSiteSelector.apply.m)) {
              val v = fieldLoad.getLeftOp
              val forwardQuery = new ForwardQuery(cell.getRowKey, new AllocVal(v, fieldLoad, v))
              val results = if (forwardQueryCache.contains(forwardQuery)) forwardQueryCache(forwardQuery) else doForwardQuery(forwardQuery)
              results.asStatementValWeightTable().cellSet().forEach(cell => {
                cell.getRowKey.getTarget match {
                  case apply: SWANStatement.ApplyFunctionRef => {
                    if ((callSiteSelector.argIdx < apply.getInvokeExpr.getArgs.size()) && apply.getInvokeExpr.getArg(callSiteSelector.argIdx).equals(cell.getColumnKey)) {
                      found.add(sr)
                    }
                  } case _ =>
                }
              })
            }
          }
          case _ =>
        }
      })
    })
    found.foreach(sr => {
      cg.methods.foreach(m => {
        getAllSingletonReads(m._2).foreach(sr2 => {
          if (!sr2.equals(sr) && sr2.staticField.equals(sr.staticField)) {
            val edge = new ControlFlowGraph.Edge(sr2, sr2.m.getCFG.getSuccsOf(sr2).iterator().next())
            val q = new ForwardQuery(edge, new AllocVal(sr2.getLeftOp, sr2, sr2.getLeftOp))
            val results = if (forwardQueryCache.contains(q)) forwardQueryCache(q) else doForwardQuery(q)
            results.asStatementValWeightTable().cellSet().forEach(cell => {
              cell.getRowKey.getStart match {
                case fieldWrite: SWANStatement.FieldWrite => {
                  if (fieldWrite.uses(cell.getColumnKey)) {
                    val q = BackwardQuery.make(cell.getRowKey, fieldWrite.getRightOp)
                    val (a, b) = isConstant(q)
                    foundConstant = foundConstant || a
                    constantValues.addAll(b)
                  }
                }
                case _ =>
              }
            })
          }
        })
      })
    })
    (foundConstant, constantValues)
  }

  private def getAllSingletonReads(m: SWANMethod): ArrayBuffer[SWANStatement.StaticFieldLoad] = {
    val original = ArrayBuffer.from(m.getStatements.toArray)
    original.filter(x => x.isInstanceOf[SWANStatement.StaticFieldLoad]).asInstanceOf[ArrayBuffer[SWANStatement.StaticFieldLoad]]
  }

  private def doForwardQuery(query: ForwardQuery, timeout: Int = 20000): ForwardBoomerangResults[Weight.NoWeight] = {
    val boomerang = getBoomerang(timeout, getForwardScope)
    val startTime = System.currentTimeMillis()
    val results = if (forwardQueryCache.contains(query)) forwardQueryCache(query) else boomerang.solve(query)
    forwardQueryCache.put(query, results)
    // System.out.println(s"query took ${System.currentTimeMillis() - startTime} ms")
    results
  }

  private def getBackwardScope: DataFlowScope = {
    new DataFlowScope {
      override def isExcluded(declaredMethod: DeclaredMethod): Boolean = {
        val sm = cg.methods(declaredMethod.getName)
        val model = sm.delegate.attribute.nonEmpty && {
          sm.delegate.attribute.get match {
            case FunctionAttribute.model => true
            case FunctionAttribute.modelOverride => true
            case _ => false
          }
        }
        val library = if (!analyzeLibraries) sm.delegate.isLibrary else false
        (!model && library) || (library && cg.edgesInto(sm).isEmpty)
      }
      override def isExcluded(method: Method): Boolean = {
        val sm = method.asInstanceOf[SWANMethod]
        val model = sm.delegate.attribute.nonEmpty && {
          sm.delegate.attribute.get match {
            case FunctionAttribute.model => true
            case FunctionAttribute.modelOverride => true
            case _ => false
          }
        }
        val library = if (analyzeLibraries) false else sm.delegate.isLibrary
        (!model && library) || (library && cg.edgesInto(sm).isEmpty)
      }
    }
  }

  private def getForwardScope: DataFlowScope = {
    new DataFlowScope {
      override def isExcluded(declaredMethod: DeclaredMethod): Boolean = {
        val sm = cg.methods(declaredMethod.getName)
        val model = sm.delegate.attribute.nonEmpty && {
          sm.delegate.attribute.get match {
            case FunctionAttribute.model => true
            case FunctionAttribute.modelOverride => true
            case _ => false
          }
        }
        val library = if (analyzeLibraries) false else sm.delegate.isLibrary
        !model && library
      }
      override def isExcluded(method: Method): Boolean = {
        val sm = method.asInstanceOf[SWANMethod]
        val model = sm.delegate.attribute.nonEmpty && {
          sm.delegate.attribute.get match {
            case FunctionAttribute.model => true
            case FunctionAttribute.modelOverride => true
            case _ => false
          }
        }
        val library = if (analyzeLibraries) false else sm.delegate.isLibrary
        !model && library
      }
    }
  }

  private def getBoomerang(timeout: Int, scope: DataFlowScope = DataFlowScope.INCLUDE_ALL): Boomerang = {
    // SPDS is buggy with backwards queries and tends to timeout or crash (StackOverflow),
    // so these options try to limit that.
    new Boomerang(cg, scope, new DefaultBoomerangOptions {

      override def getArrayStrategy: BoomerangOptions.ArrayStrategy = ArrayStrategy.DISABLED

      override def typeCheck(): Boolean = false

      override def trackStrings(): Boolean = false

      override def trackNullAssignments(): Boolean = false

      override def analysisTimeoutMS(): Int = timeout

      override def maxUnbalancedCallDepth(): Int = 5

      override def maxFieldDepth(): Int = 5

      override def maxCallDepth(): Int = 5

      override def trackDataFlowPath(): Boolean = false

      override def handleMaps(): Boolean = false
    })
  }
}

object CryptoAnalysis {
  
  object ResultType extends Enumeration {
    type Type = Value
    
    val RULE_1: ResultType.Type = Value
    val RULE_2: ResultType.Type = Value
    val RULE_3: ResultType.Type = Value
    val RULE_4: ResultType.Type = Value
    val RULE_5: ResultType.Type = Value
    val RULE_7: ResultType.Type = Value

    def toDisplayString(t: ResultType.Value): String = {
      t match {
        case CryptoAnalysis.ResultType.RULE_1 => "ECB"
        case CryptoAnalysis.ResultType.RULE_2 => "IV"
        case CryptoAnalysis.ResultType.RULE_3 => "KEY"
        case CryptoAnalysis.ResultType.RULE_4 => "SALT"
        case CryptoAnalysis.ResultType.RULE_5 => "ITERATION"
        case CryptoAnalysis.ResultType.RULE_7 => "PASSWORD"
      }
    }
  }

  class Results() {
    val values: mutable.HashSet[Result] = mutable.HashSet.empty

    def add(r: Result): Unit = values.add(r)

    def writeResults(f: File): Unit = {
      val fw = new FileWriter(f)
      try {
        val j = new ArrayBuffer[ujson.Obj]
        values.foreach(r => {
          val json = ujson.Obj("name" -> CryptoAnalysis.ResultType.toDisplayString(r.tpe))
          json("message") = r.message
          json("location") = if (r.pos.nonEmpty) r.pos.get.toString else "none"
          j.append(json)
        })
        val finalJson = ujson.Value(j)
        fw.write(finalJson.render(2))
      } finally {
        fw.close()
      }
    }
  }
  
  class Result(val tpe: ResultType.Value, val pos: Option[Position], val message: String)
}
