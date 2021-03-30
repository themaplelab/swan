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
    val timeElapsed = (System.nanoTime() - startTime) / 1000000000
    if (timeElapsed >= min) {
      Logging.printInfo(String.format("Done %s %d %s in %02dm%02ds (%d %s/s)",
        action, value, unit, timeElapsed / 60, timeElapsed % 60, value / timeElapsed, unit))
    }
  }

}
