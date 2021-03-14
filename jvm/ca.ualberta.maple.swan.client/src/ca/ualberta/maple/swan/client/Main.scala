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

import ca.ualberta.maple.swan.ir.{CanFunction, Literal, Operator, Position}
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.parser.SILParser

import scala.collection.mutable.ArrayBuffer

// This is an experimental driver for finding inefficient API usage.
// It does not use dataflow analysis.

object Main {

  class QueryResults(val found: Boolean, val msg: String, val pos: Option[Position])

  val desiredAccuracyValues: Array[String] = Array(
    "kCLLocationAccuracyHundredMeters",
    "kCLLocationAccuracyBest",
    "kCLLocationAccuracyKilometer",
    "kCLLocationAccuracyBestForNavigation"
  )

  def desiredAccuracy(c: CanFunction, value: String): QueryResults = {
    val msg = "desiredAccuracy setting: " + value
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
            if (field == "") {
              global = true
            }
          }
          case _ =>
        }
      })
    })
    new QueryResults(global && desiredAccuracy, msg, position)
  }

  def distanceFilter(c: CanFunction): QueryResults = {
    var float: Float = -1
    var distanceFilter = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.distanceFilter!setter.foreign") {
              distanceFilter = true
              position = opdef.position
            }
          }
          case Operator.literal(_, literal) => {
            literal match {
              case Literal.float(value) => {
                float = value
              }
              case _ =>
            }
          }
          case _ =>
        }
      })
    })
    val msg = "distanceFilter setting: " + float.toString
    new QueryResults(float > 0 && distanceFilter, msg, position)
  }

  def startUpdatingLocation(c: CanFunction): QueryResults = {
    val msg = "startUpdatingLocation method"
    var found = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.startUpdatingLocation!foreign") {
              found = true
              position = opdef.position
            }
          }
          case _ =>
        }
      })
    })
    new QueryResults(found, msg, position)
  }

  def printQueryResult(queryResults: QueryResults): Unit = {
    if (queryResults.found) {
      val sb = new StringBuilder
      sb.append("\nDetected ")
      sb.append(queryResults.msg)
      if (queryResults.pos.nonEmpty) {
        val p = queryResults.pos.get
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
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      throw new RuntimeException("Expected 1 argument: the swan-fir file path")
    }

    val swanDir = new File(args(0))
    if (!swanDir.exists()) {
      throw new RuntimeException("Given path does not exist")
    }

    val silFiles = swanDir.listFiles((_: File, name: String) => name.endsWith(".sil"))
    for (sil <- silFiles) {
      val silParser = new SILParser(sil.toPath)
      val silModule = silParser.parseModule()
      val swirlModule = SWIRLGen.translateSILModule(silModule)
      val canSwirlModule = SWIRLPass.runPasses(swirlModule)

      canSwirlModule.functions.foreach(f => {
        desiredAccuracyValues.foreach(v => {
          printQueryResult(desiredAccuracy(f, v))
        })
        printQueryResult(startUpdatingLocation(f))
        printQueryResult(distanceFilter(f))
      })
    }
  }
}