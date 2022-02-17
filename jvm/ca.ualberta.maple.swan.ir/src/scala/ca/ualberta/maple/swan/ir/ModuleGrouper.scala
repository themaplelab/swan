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

package ca.ualberta.maple.swan.ir

import java.io.File

import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

object ModuleGrouper {

  // We throw exceptions for unexpected merging behaviour, but really
  // it is difficult to detect when user functions conflict because
  // modules often have implementations for the same builtins.
  // Would be also good to check that the implementations are equal.
  // The model module must be merged last.
  // TODO: The current function merging is likely inefficient.
  private def merge(toMerge: ArrayBuffer[CanFunction],
            existingFunctions: mutable.HashMap[String, CanFunction],
            entries: mutable.HashMap[String, CanFunction], models: mutable.HashMap[String, CanFunction],
            mains: mutable.HashMap[String, CanFunction], others: mutable.HashMap[String, CanFunction],
            stubs: mutable.HashMap[String, CanFunction], linked: mutable.HashMap[String, CanFunction],
            forceAdd: Boolean = false): Unit = {
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
                  case FunctionAttribute.model => add(f, FunctionAttribute.modelOverride)
                  case _ => throwException("unexpected")
                }
              }
              case _ => throwException("unexpected")
            }
          } else { // to add has no attribute
            existing.attribute.get match {
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
            if (forceAdd) add(f)
          }
        }
      } else {
        if (f.attribute.nonEmpty) {
          f.attribute.get match {
            case FunctionAttribute.model => // Don't add unneeded models
            case _ => add(f)
          }
        } else {
          add(f)
        }
      }
    })
  }

  def group(modules: ArrayBuffer[CanModule], existingGroup: ModuleGroup = null, changedFiles: ArrayBuffer[File] = null): ModuleGroup = {
    val functions = ArrayBuffer.empty[CanFunction]
    val entries = mutable.HashMap.empty[String, CanFunction]
    val models = mutable.HashMap.empty[String, CanFunction]
    val mains = mutable.HashMap.empty[String, CanFunction]
    val others = mutable.HashMap.empty[String, CanFunction]
    val stubs = mutable.HashMap.empty[String, CanFunction]
    val linked = mutable.HashMap.empty[String, CanFunction]
    val existingFunctions = new mutable.HashMap[String, CanFunction]()
    var ddgs = new mutable.HashMap[String, DynamicDispatchGraph]
    val silMap = new SILMap
    val metas = ArrayBuffer.empty[ModuleMetadata]
    if (existingGroup != null) {
      merge(existingGroup.functions, existingFunctions, entries, models, mains, others, stubs, linked)
      ddgs = existingGroup.ddgs
      if (existingGroup.silMap.nonEmpty) {
        silMap.combine(existingGroup.silMap.get)
      }
      metas.appendAll(existingGroup.metas)
    }

    // Move model module to end
    val idx = modules.indexWhere(m => m.toString == "models")
    if (idx >= 0) {
      val modelModule = modules.remove(idx)
      modules.append(modelModule)
    }
    modules.foreach(module => {
      val changed = if (changedFiles != null) changedFiles.exists(f => f.getName == module.toString) else false
      if (changed) {
        Logging.printInfo("Merging " + module.functions.length + " functions into existing group")
      }
      merge(module.functions, existingFunctions, entries, models, mains, others, stubs, linked, changed)
      if (module.ddg.nonEmpty && !changed) {
        val name = module.toString
        if (ddgs.contains(name)) {
          ddgs.remove(name)
        }
        ddgs.put(name, module.ddg.get)
      }
      if (!changed) {
        // This will need to be updated if metadata contains more information
        // that can change base don module content
        if (!metas.exists(m => m.toString == module.meta.toString)) metas.append(module.meta)
      }
      // TODO: see if this even works after serialization
      if (module.silMap.nonEmpty) {
        silMap.combine(module.silMap.get)
      }
    })

    functions.appendAll(entries.values)
    functions.appendAll(mains.values)
    functions.appendAll(models.values)
    functions.appendAll(linked.values)
    functions.appendAll(others.values)
    functions.appendAll(stubs.values)

    new ModuleGroup(functions, entries.values.to(immutable.HashSet), ddgs,
      if (silMap.nonEmpty()) Some(silMap) else None, None, metas)
  }
}
