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

// TODO: The current merging is likely inefficient.
object ModuleGrouper {

  // Would be also good to check that the implementations are equal
  def merge(toMerge: ArrayBuffer[CanFunction],
            existingFunctions: mutable.HashMap[String, CanFunction],
            entries: mutable.HashMap[String, CanFunction], models: mutable.HashMap[String, CanFunction],
            mains: mutable.HashMap[String, CanFunction], others: mutable.HashMap[String, CanFunction],
            stubs: mutable.HashMap[String, CanFunction], linked: mutable.HashMap[String, CanFunction],
            persistentAdd: Boolean = false): Unit = {
    def add(f: CanFunction, attr: FunctionAttribute = null): Unit = {
      // Ordering for convenience
      if (attr != null) {
        f.attribute = Some(attr)
      }
      if (entries.contains(f.name)) entries.remove(f.name)
      if (mains.contains(f.name)) mains.remove(f.name)
      if (models.contains(f.name)) models.remove(f.name)
      if (mains.contains(f.name)) mains.remove(f.name)
      if (others.contains(f.name)) others.remove(f.name)
      if (stubs.contains(f.name)) stubs.remove(f.name)
      if (linked.contains(f.name)) linked.remove(f.name)
      if (f.name.startsWith("main_")) {
        mains.put(f.name, f)
      } else {
        if (f.attribute.nonEmpty) {
          f.attribute.get match {
            case FunctionAttribute.entry => entries.put(f.name, f)
            case FunctionAttribute.model => models.put(f.name, f)
            case FunctionAttribute.stub => stubs.put(f.name, f)
            case FunctionAttribute.linked => linked.put(f.name, f)
            case _ => others.put(f.name, f)
          }
        } else {
          others.put(f.name, f)
        }
      }
      if (existingFunctions.contains(f.name)) {
        existingFunctions(f.name) = f
      } else {
        existingFunctions.put(f.name, f)
      }
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
                  case FunctionAttribute.coroutine => add(f)
                  case _ => throwException("unexpected")
                }
              }
              case FunctionAttribute.stub => {
                f.attribute.get match {
                  case FunctionAttribute.stub => // ignore
                  case FunctionAttribute.model => add(f)
                  // Hides coroutine attribute, maybe we should use multiple attributes
                  case FunctionAttribute.coroutine => add(f, FunctionAttribute.linked)
                  case _ => throwException("unexpected")
                }
              }
              case FunctionAttribute.coroutine => {
                f.attribute.get match {
                  case FunctionAttribute.stub => existing.attribute = Some(FunctionAttribute.linked)
                  case FunctionAttribute.coroutine => add(f)
                  case FunctionAttribute.model => add(f)
                  case _ => throwException("unexpected")
                }
              }
              case FunctionAttribute.linked => {
                f.attribute.get match {
                  case FunctionAttribute.stub => // ignore
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
              case FunctionAttribute.linked => // ignore
              case _ => throwException("unexpected")
            }
          }
        } else { // Existing has no attribute
          if (f.attribute.nonEmpty) { // to add has an attribute
            f.attribute.get match {
              case FunctionAttribute.model => add(f, FunctionAttribute.modelOverride)
              case FunctionAttribute.stub => existing.attribute = Some(FunctionAttribute.linked)
              case _ => throwException("unexpected")
            }
          } else { // to add also has no attribute
            // Duplicates are expected due to inlining, builtin implementations, etc
            if (persistentAdd) add(f)
          }
        }
      } else {
        add(f)
      }
    })
  }

  def group(modules: ArrayBuffer[CanModule], existingGroup: ModuleGroup = null): ModuleGroup = {
    val functions = ArrayBuffer.empty[CanFunction]
    val entries = mutable.HashMap.empty[String, CanFunction]
    val models = mutable.HashMap.empty[String, CanFunction]
    val mains = mutable.HashMap.empty[String, CanFunction]
    val others = mutable.HashMap.empty[String, CanFunction]
    val stubs = mutable.HashMap.empty[String, CanFunction]
    val linked = mutable.HashMap.empty[String, CanFunction]
    val existingFunctions = new mutable.HashMap[String, CanFunction]()
    val ddgs = ArrayBuffer.empty[DynamicDispatchGraph]
    val silMap = new SILMap
    val metas = ArrayBuffer.empty[ModuleMetadata]
    if (existingGroup != null) {
      merge(existingGroup.functions, existingFunctions, entries, models, mains, others, stubs, linked)
      ddgs.appendAll(existingGroup.ddgs)
      silMap.combine(existingGroup.silMap)
      metas.appendAll(existingGroup.metas)
    }
    modules.foreach(module => {
      if (existingGroup != null) {
        Logging.printInfo("Merging " + module.functions.length + " function(s) into existing group")
      }
      merge(module.functions, existingFunctions, entries, models, mains, others, stubs, linked, existingGroup != null)
      if (module.ddg.nonEmpty) {
        ddgs.append(module.ddg.get)
      }
      silMap.combine(module.silMap)
      if (existingGroup == null) metas.append(module.meta)
    })
    functions.appendAll(entries.values)
    functions.appendAll(mains.values)
    functions.appendAll(models.values)
    functions.appendAll(linked.values)
    functions.appendAll(others.values)
    functions.appendAll(stubs.values)
    new ModuleGroup(functions, entries.values.to(immutable.HashSet), ddgs, silMap, metas)
  }
}
