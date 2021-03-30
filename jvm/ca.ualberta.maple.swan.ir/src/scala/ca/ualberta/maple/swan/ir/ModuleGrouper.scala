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

  def merge(functions: mutable.HashMap[String, CanFunction], f: CanFunction): Unit = {
    // Merge functions - need to handle both directions of overriding
    if (functions.contains(f.name)) {
      val existing = functions(f.name)
      def throwException(msg: String): Unit = {
        throw new RuntimeException(msg +
          "\n  existing: " + existing.name + " attr: " + existing.attribute +
          "\n  adding: " + f.name + " attr: " + f.attribute)
      }
      def add(attr: Option[FunctionAttribute] = f.attribute): Unit = {
        f.attribute = attr
        functions.put(f.name, f)
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
                case FunctionAttribute.model => add()
                case FunctionAttribute.coroutine => add()
                case _ => throwException("unexpected")
              }
            }
            case _ => throwException("unexpected")
          }
        } else { // to add has no attribute
          existing.attribute.get match {
            case FunctionAttribute.model => existing.attribute = Some(FunctionAttribute.modelOverride)
            case FunctionAttribute.stub => add(Some(FunctionAttribute.linked))
            case FunctionAttribute.modelOverride => // ignore
            case _ => throwException("unexpected")
          }
        }
      } else { // Existing has no attribute
        if (f.attribute.nonEmpty) { // to add has an attribute
          f.attribute.get match {
            case FunctionAttribute.model => add(Some(FunctionAttribute.modelOverride))
            case FunctionAttribute.stub => // ignore
            case _ => throwException("unexpected")
          }
        } else { // to add also has no attribute
          // Duplicates are expected due to inlining, builtin implementations, etc
        }
      }
    } else {
      functions.put(f.name, f)
    }
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
        merge(functions, f)
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
