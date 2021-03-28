/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir

import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

object ModuleGrouper {

  def group(modules: ArrayBuffer[CanModule]): ModuleGroup = {
    val sb = new StringBuilder()
    sb.append("Grouping \n")
    modules.foreach(m => {
      sb.append("  ")
      sb.append(m)
      sb.append("\n")
    })
    Logging.printInfo(sb.toString())
    val entries = mutable.HashSet.empty[CanFunction]
    val functions = new mutable.HashMap[String, CanFunction]()
    val ddgs = ArrayBuffer.empty[DynamicDispatchGraph]
    val silMap = new SILMap
    val metas = ArrayBuffer.empty[ModuleMetadata]
    modules.foreach(module => {
      if (module.entryFunction.nonEmpty) {
        entries.add(module.entryFunction.get)
      }
      module.functions.foreach(f => {
        if (functions.contains(f.name) && {
          if (f.attribute.nonEmpty) {
            f.attribute.get match {
              case FunctionAttribute.stub => false
              case _ => true
            }
          } else true
        }) {
          val existing = functions(f.name)
          if (existing.attribute.nonEmpty) {
            existing.attribute.get match {
              case FunctionAttribute.stub => {
                f.attribute = Some(FunctionAttribute.linked)
              }
              case _ => throw new RuntimeException("Duplicate function found that isn't a stub: " + f.name)
            }
          }
        }
        functions.put(f.name, f)
      })
      if (module.ddg.nonEmpty) {
        ddgs.append(module.ddg.get)
      }
      silMap.combine(module.silMap)
      metas.append(module.meta)
    })
    new ModuleGroup(functions.values.to(ArrayBuffer), entries.to(immutable.HashSet), ddgs, silMap, metas)
  }
}
