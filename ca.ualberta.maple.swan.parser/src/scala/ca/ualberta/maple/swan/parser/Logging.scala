/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.parser

import java.util.Date
import java.util.concurrent.TimeUnit

object Logging {

  class ProgressBar(val label: String, val max: Int, val useSpinner: Boolean) {

    private val spinner = Array('|', '/', '-', '\\')
    private var spinIndex = 0

    val startTime = System.currentTimeMillis()

    update(0)

    def update(newVal: Int): Unit = {
      print(generateNewString(newVal))
    }

    def generateNewString(at: Int): String = {
      spinIndex = if (spinIndex + 1 == spinner.length) { 0 } else { spinIndex + 1 }
      val sb = new StringBuilder()
      sb.append('\r')
        .append(label)
        .append(" [")
      val progressBarLength = 78 - sb.toString().length - 5
      val percentage = at.toFloat / max.toFloat
      val stars = "*" * ((progressBarLength * percentage).toInt - (if (at >= max) 0 else 1))
      sb.append(stars)
        .append(if (at >= max) "" else if (useSpinner) spinner(spinIndex) else ">")
        .append(" " * (progressBarLength - stars.length - 1))
        .append("] ")
      val percent = (percentage * 100).toInt.toString + "%"
      sb.append(" " * (4 - percent.length))
        .append(percent)
        .toString()
    }
    def print(s: String): Unit = {
      System.out.flush()
      System.out.print(s)
    }

    def done(): Unit = {
      print(generateNewString(max))
      print("\n")
      val millis = System.currentTimeMillis() - startTime
      val timeTaken = String.format("%02d min, %02d sec",
        TimeUnit.MILLISECONDS.toMinutes(millis),
        TimeUnit.MILLISECONDS.toSeconds(millis) -
          TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
      );
      System.out.println(label + " took " + timeTaken)
    }
  }
}
