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

package ca.ualberta.maple.swan.utils

object Logging {

  def printInfo(msg: String): Unit = {
    System.out.println("["+Thread.currentThread().getName+"] "+msg)
  }

  def printTimeStamp(min: Int, startTime: Long, action: String, value: Long, unit: String): Unit = {
    val millis = (System.nanoTime() - startTime) / 1000000
    val seconds: Double = millis.toDouble / 1000
    if (seconds >= min) {
      Logging.printInfo(String.format("Done %s %d %s in %d.%03ds (%d %s/s)",
        action, value, unit, millis / 1000, millis % 1000, (value / seconds).toInt, unit))
    }
  }

  def printTimeStampNoRate(min: Int, startTime: Long, action: String, value: Long, unit: String): Unit = {
    val millis = (System.nanoTime() - startTime) / 1000000
    val seconds: Double = millis.toDouble / 1000
    if (seconds >= min) {
      Logging.printInfo(String.format("Done %s %d %s in %d.%03ds",
        action, value, unit, millis / 1000, millis % 1000))
    }
  }

  def printTimeStampSimple(min: Int, startTime: Long, action: String): Unit = {
    val millis = (System.nanoTime() - startTime) / 1000000
    val seconds: Double = millis.toDouble / 1000
    if (seconds >= min) {
      Logging.printInfo(String.format("Done %s in %d.%03ds",
        action, millis / 1000, millis % 1000))
    }
  }

}
