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

import boomerang.scene.CallGraph
import ca.ualberta.maple.swan.ir.{CanFunction, CanModule, Constants}
import com.google.common.collect.Sets

// TODO: iOS lifecycle
class SWANCallGraph(val module: CanModule) extends CallGraph {

  private val methods = Sets.newHashSet[SWANMethod]

  module.functions.foreach(f => {
    val m = makeMethod(f)
    if (f.name == Constants.fakeMain) {
      this.getEntryPoints.add(m)
    }
  })

  def makeMethod(f: CanFunction): SWANMethod = {
    val m = new SWANMethod(f)
    methods.add(m)
    m
  }

}
