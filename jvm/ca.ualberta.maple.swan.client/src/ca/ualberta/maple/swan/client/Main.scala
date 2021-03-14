/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.client

import java.io.File

import ca.ualberta.maple.swan.ir.{CanFunction, Operator, Position}
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.parser.SILParser

import scala.collection.mutable.ArrayBuffer

// This is an experimental driver for finding inefficient API usage.
// It does not use dataflow analysis.

object Main {

  def genQueries(): ArrayBuffer[CanFunction => (Boolean, String, Option[Position])] = {
    val qs = ArrayBuffer.empty[CanFunction => (Boolean, String, Option[Position])]
    qs.append((c: CanFunction) => {
      val msg = "inefficient CLLocationManager parameter"
      var global = false
      var desiredAccuracy = false
      var position: Option[Position] = None
      c.blocks.foreach(b => {
        b.operators.foreach(opdef => {
          opdef.operator match {
            case Operator.builtinRef(_, name) => {
              if (name == "#CLLocationManager.desiredAccuracy!setter.foreign") {
                desiredAccuracy = true
                position = opdef.position
              }
            }
            case Operator.singletonRead(_, _, field) => {
              if (field == "kCLLocationAccuracyHundredMeters") {
                global = true
              }
            }
            case _ =>
          }
        })
      })
      (global && desiredAccuracy, msg, position)
    })
    qs
  }

  def printQueryResult(msg: String, pos: Option[Position]): Unit = {
    val sb = new StringBuilder
    sb.append("\nDetected ")
    sb.append(msg)
    if (pos.nonEmpty) {
      val p = pos.get
      sb.append(" at\n  ")
      sb.append(p.path)
      sb.append(":")
      sb.append(p.line)
      sb.append(":")
      sb.append(p.col)
    } else {
      sb.append(" (no source available)")
    }
    System.out.println(sb.toString())
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      throw new RuntimeException("Expected 1 argument: the swan-fir file path")
    }

    val swanDir = new File(args(0))
    if (!swanDir.exists()) {
      throw new RuntimeException("Given path does not exist")
    }

    val queries = genQueries()

    val silFiles = swanDir.listFiles((_: File, name: String) => name.endsWith(".sil"))
    for (sil <- silFiles) {
      val silParser = new SILParser(sil.toPath)
      val silModule = silParser.parseModule()
      val swirlModule = SWIRLGen.translateSILModule(silModule)
      val canSwirlModule = SWIRLPass.runPasses(swirlModule)

      canSwirlModule.functions.foreach(f => {
        queries.foreach(query => {
          val results = query(f)
          if (results._1) {
            printQueryResult(results._2, results._3)
          }
        })
      })
    }
  }
}