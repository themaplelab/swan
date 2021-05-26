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

package ca.ualberta.maple.swan.testclient

import java.io.File

import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.ir.raw.SWIRLGen
import ca.ualberta.maple.swan.ir.{CanFunction, Literal, Operator, Position}
import ca.ualberta.maple.swan.parser.SILParser

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

  val activityTypeValues: Array[(String, String)] = Array(
    ("#CLActivityType.airborne!enumelt", "CLActivityType.airborne") ,
    ("#CLActivityType.automotiveNavigation!enumelt", "CLActivityType.automotiveNavigation"),
    ("#CLActivityType.other!enumelt", "CLActivityType.other"),
    ("#CLActivityType.otherNavigation!enumelt", "CLActivityType.otherNavigation"),
    ("#CLActivityType.fitness!enumelt", "CLActivityType.fitness")
  )

  def desiredAccuracy(c: CanFunction, value: String): QueryResults = {
    val msg = "desiredAccuracy setting: " + value
    var global = false
    var setter = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.desiredAccuracy!setter.foreign") {
              setter = true
              position = opdef.position
            }
          }
          case Operator.singletonRead(_, _, field) => {
            if (field == value) {
              global = true
            }
          }
          case _ =>
        }
      })
    })
    new QueryResults(global && setter, msg, position)
  }

  def activityType(c: CanFunction, value: (String, String)): QueryResults = {
    val msg = "activityType setting: " + value._2
    var fieldWrite = false
    var literal = false
    var setter = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.activityType!setter.foreign") {
              setter = true
              position = opdef.position
            }
          }
          case Operator.fieldWrite(_, _, field, _) => {
            if (field == "type") {
              fieldWrite = true
            }
          }
          case Operator.literal(_, l) => {
            l match {
              case Literal.string(v) => {
                if (value._1 == v) {
                  literal = true
                }
              }
              case _ =>
            }
          }
          case _ =>
        }
      })
    })
    new QueryResults(fieldWrite && setter && literal, msg, position)
  }

  def distanceFilter(c: CanFunction): QueryResults = {
    var float: Double = 0
    var floatSet = false
    var setter = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.distanceFilter!setter.foreign") {
              setter = true
              position = opdef.position
            }
          }
          case Operator.literal(_, literal) => {
            literal match {
              case Literal.float(value) => {
                float = value
                floatSet = true
              }
              case _ =>
            }
          }
          case _ =>
        }
      })
    })
    val msg = "distanceFilter setting: " + float.toString
    new QueryResults(floatSet && setter, msg, position)
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

  def startMonitoringSignificantLocationChanges(c: CanFunction): QueryResults = {
    val msg = "startMonitoringSignificantLocationChanges method"
    var found = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.startMonitoringSignificantLocationChanges!foreign") {
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

  def startMonitoringVisits(c: CanFunction): QueryResults = {
    val msg = "startMonitoringVisits method"
    var found = false
    var position: Option[Position] = None
    c.blocks.foreach(b => {
      b.operators.foreach(opdef => {
        opdef.operator match {
          case Operator.builtinRef(_, name) => {
            if (name == "#CLLocationManager.startMonitoringVisits!foreign") {
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
      throw new RuntimeException("Expected 1 argument: the swan-dir file path")
    }

    val swanDir = new File(args(0))
    if (!swanDir.exists()) {
      throw new RuntimeException("Given path does not exist")
    }

    val silFiles = swanDir.listFiles((_: File, name: String) => name.endsWith(".sil"))
    for (sil <- silFiles) {
      val silParser = new SILParser(sil.toPath)
      val silModule = silParser.parseModule()
      val swirlModule = new SWIRLGen().translateSILModule(silModule)
      val canSwirlModule = new SWIRLPass().runPasses(swirlModule)

      canSwirlModule.functions.foreach(f => {
        desiredAccuracyValues.foreach(v => {
          printQueryResult(desiredAccuracy(f, v))
        })
        activityTypeValues.foreach(v => {
          printQueryResult(activityType(f, v))
        })
        printQueryResult(startUpdatingLocation(f))
        printQueryResult(startMonitoringVisits(f))
        printQueryResult(distanceFilter(f))
        printQueryResult(startMonitoringSignificantLocationChanges(f))
      })
    }
  }
}