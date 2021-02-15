/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.spds

import java.util
import java.util.Set

import boomerang.scene.{CallGraph, Method}
import ca.ualberta.maple.swan.ir.{CanFunction, CanModule, Constants}
import com.google.common.collect.Sets

import scala.util.control.Breaks.{break, breakable}

// TODO: iOS lifecycle
// TODO: dynamic CG
class SWANCallGraph(val module: CanModule) extends CallGraph {

  private val methods = Sets.newHashSet[SWANMethod]

  breakable {
    module.functions.foreach(f => {
      if (f.name == Constants.fakeMain) {
        this.getEntryPoints.add(makeMethod(f))
        break()
      }
    })
  }

  def makeMethod(f: CanFunction): SWANMethod = {
    val m = new SWANMethod(f)
    methods.add(m)
    m
  }

}
