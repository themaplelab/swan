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

  def merge(toMerge: ArrayBuffer[CanFunction],
            existingFunctions: mutable.HashMap[String, CanFunction],
            entries: ArrayBuffer[CanFunction], models: ArrayBuffer[CanFunction],
            others: ArrayBuffer[CanFunction], stubs: ArrayBuffer[CanFunction],
            linked: ArrayBuffer[CanFunction]): Unit = {
    def add(f: CanFunction, attr: FunctionAttribute = null): Unit = {
      // Ordering for convenience
      if (attr != null) {
        f.attribute = Some(attr)
      }
      if (f.name.startsWith("main_")) {
        others.insert(0, f)
      } else {
        if (f.attribute.nonEmpty) {
          f.attribute.get match {
            case FunctionAttribute.entry => entries.append(f)
            case FunctionAttribute.model => models.append(f)
            case FunctionAttribute.stub => stubs.append(f)
            case FunctionAttribute.linked => linked.append(f)
            case _ => others.append(f)
          }
        } else {
          others.append(f)
        }
      }
      existingFunctions.put(f.name, f)
    }
    toMerge.foreach(f => {
      if (existingFunctions.contains(f.name)) {
        val existing = existingFunctions(f.name)
        def throwException(msg: String): Unit = {
          throw new RuntimeException(msg +
            "\n  existing: " + existing.name + " attr: " + existing.attribute +
            "\n  adding: " + f.name + " attr: " + f.attribute)
        }
        if (existing.attribute.nonEmpty) { // Existing has an attribute
          if (f.attribute.nonEmpty) { // to add also has an attribute
            existing.attribute.get match {
              case FunctionAttribute.model => {
                f.attribute.get match {
                  case FunctionAttribute.stub => // ignore
                  case _ => throwException("unexpected")
                }
              }
              case FunctionAttribute.stub => {
                f.attribute.get match {
                  case FunctionAttribute.stub => // ignore
                  case FunctionAttribute.model => add(f)
                  case FunctionAttribute.coroutine => add(f)
                  case _ => throwException("unexpected")
                }
              }
              case _ => throwException("unexpected")
            }
          } else { // to add has no attribute
            existing.attribute.get match {
              case FunctionAttribute.model => existing.attribute = Some(FunctionAttribute.modelOverride)
              case FunctionAttribute.stub => add(f, FunctionAttribute.linked)
              case FunctionAttribute.modelOverride => // ignore
              case _ => throwException("unexpected")
            }
          }
        } else { // Existing has no attribute
          if (f.attribute.nonEmpty) { // to add has an attribute
            f.attribute.get match {
              case FunctionAttribute.model => add(f, FunctionAttribute.modelOverride)
              case FunctionAttribute.stub => // ignore
              case _ => throwException("unexpected")
            }
          } else { // to add also has no attribute
            // Duplicates are expected due to inlining, builtin implementations, etc
          }
        }
      } else {
        add(f)
      }
    })
  }

  def group(modules: ArrayBuffer[CanModule]): ModuleGroup = {
    val sb = new StringBuilder()
    sb.append("Grouping \n")
    modules.foreach(m => {
      sb.append("  ")
      sb.append(m)
      sb.append("\n")
    })
    Logging.printInfo(sb.toString())
    val functions = ArrayBuffer.empty[CanFunction]
    val entries = ArrayBuffer.empty[CanFunction]
    val models = ArrayBuffer.empty[CanFunction]
    val others = ArrayBuffer.empty[CanFunction]
    val stubs = ArrayBuffer.empty[CanFunction]
    val linked = ArrayBuffer.empty[CanFunction]
    val existingFunctions = new mutable.HashMap[String, CanFunction]()
    val ddgs = ArrayBuffer.empty[DynamicDispatchGraph]
    val silMap = new SILMap
    val metas = ArrayBuffer.empty[ModuleMetadata]
    modules.foreach(module => {
      merge(module.functions, existingFunctions, entries, models, others, stubs, linked)
      if (module.ddg.nonEmpty) {
        ddgs.append(module.ddg.get)
      }
      silMap.combine(module.silMap)
      metas.append(module.meta)
    })
    functions.appendAll(entries)
    functions.appendAll(models)
    functions.appendAll(linked)
    functions.appendAll(others)
    functions.appendAll(stubs)
    new ModuleGroup(functions, entries.to(immutable.HashSet), ddgs, silMap, metas)
  }
}
