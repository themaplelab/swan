/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
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

}
