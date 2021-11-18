/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use cg file except in compliance with the License.
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
 * cg software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

package ca.ualberta.maple.swan.spds

import boomerang.scene._
import boomerang.{BackwardQuery, Boomerang, DefaultBoomerangOptions}
import ca.ualberta.maple.swan.ir._
import ca.ualberta.maple.swan.ir.canonical.SWIRLPass
import ca.ualberta.maple.swan.spds.structures._
import ca.ualberta.maple.swan.utils.Logging

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class CGAggregates(val cg: SWANCallGraph, val entryPoints: mutable.LinkedHashSet[SWANMethod], val startMethod: SWANMethod) {
  type A = Int
  val instantiatedTypes = new mutable.HashSet[String]()
  // Mapping of methods to # of instantiated types
  val methodCount = new mutable.HashMap[SWANMethod, A]
  // Mapping of block start stmts to # of instantiated types
  val blockCount = new mutable.HashMap[Statement, A]

  def size: A = instantiatedTypes.size
}

class CallGraphConstruction(moduleGroup: ModuleGroup) {

  private final val uninterestingEntryPoints = Array(
    ".__deallocating_deinit",
    ".deinit",
    ".modify"
    // reabstraction thunk helper from*
  )

  private val methods = new mutable.HashMap[String, SWANMethod]()
  private val cg = new SWANCallGraph(moduleGroup, methods)
  private val mainEntryPoints = new mutable.HashSet[SWANMethod]
  private val otherEntryPoints = new mutable.HashSet[SWANMethod]

  private val builder = new SWIRLBuilder
  private var modified = false

  // stats for entire CG
  var trivialEdges = 0
  var virtualEdges = 0
  var queriedEdges = 0
  var totalQueries = 0
  var fruitlessQueries = 0

  val debugInfo = new mutable.HashMap[CanOperator, mutable.HashSet[String]]()

  // The functions traverseBlock, traverseMethod are mutually recursive
  // and traverse blocks and methods to build the call graph

  // Traverse a block
  def traverseBlock(b: ArrayBuffer[SWANStatement])(implicit m: SWANMethod, ag: CGAggregates): Unit = {
    if (ag.blockCount.contains(b(0))) {
      if (ag.blockCount(b(0)) == ag.size) {
        return
      }
    }
    ag.blockCount.put(b(0), ag.size)
    b.foreach {
      case applyStmt: SWANStatement.ApplyFunctionRef => {
        val edge = new ControlFlowGraph.Edge(m.getCFG.getPredsOf(applyStmt).iterator().next(), applyStmt)
        m.delegate.symbolTable(applyStmt.inst.functionRef.name) match {
          case SymbolTableEntry.operator(_, operator) => {
            operator match {
              case Operator.functionRef(_, name) => {
                val target = methods(name)
                if (addCGEdge(m, target, applyStmt, edge)) trivialEdges += 1
                traverseMethod(target)
              }
              case Operator.dynamicRef(_, obj, index) => {
                moduleGroup.ddgs.foreach(ddg => {
                  val functionNames = ddg._2.query(index, Some(ag.instantiatedTypes))
                  functionNames.foreach(name => {
                    val target = methods(name)
                    if (addCGEdge(m, target, applyStmt, edge)) virtualEdges += 1
                    traverseMethod(target)
                  })
                })
              }
              case Operator.builtinRef(_, name) => {
                if (methods.contains(name)) {
                  val target = methods(name)
                  if (addCGEdge(m, target, applyStmt, edge)) trivialEdges += 1
                  traverseMethod(target)
                }
              }
              case _ => queryRef(applyStmt, m)
            }
          }
          case _: SymbolTableEntry.argument => queryRef(applyStmt, m)
          case multiple: SymbolTableEntry.multiple => {
            multiple.operators.foreach {
              case Operator.functionRef(_, name) => {
                val target = methods(name)
                if (addCGEdge(m, target, applyStmt, edge)) trivialEdges += 1
                traverseMethod(target)
              }
              case Operator.dynamicRef(_, obj, index) => {
                moduleGroup.ddgs.foreach(ddg => {
                  val functionNames = ddg._2.query(index, Some(ag.instantiatedTypes))
                  functionNames.foreach(name => {
                    val target = methods(name)
                    if (addCGEdge(m, target, applyStmt, edge)) virtualEdges += 1
                    traverseMethod(target)
                  })
                })
              }
              case Operator.builtinRef(_, name) => {
                if (methods.contains(name)) {
                  val target = methods(name)
                  if (addCGEdge(m, target, applyStmt, edge)) trivialEdges += 1
                  traverseMethod(target)
                }
              }
              case _ => queryRef(applyStmt, m)
            }
          }
        }
      }
      case _ =>
    }
    m.getControlFlowGraph.getSuccsOf(b.last).forEach(nextBlock => {
      traverseBlock(m.getCFG.blocks(nextBlock.asInstanceOf[SWANStatement]).stmts)
    })
  }

  def traverseMethod(m: SWANMethod)(implicit ag: CGAggregates): Unit = {
    if (m.delegate.attribute.nonEmpty && m.delegate.attribute.get == FunctionAttribute.stub) {
      return
    }

    if (m.getName.startsWith("closure ") || m.getName.startsWith("reabstraction thunk")) {
      return
    }

    if (ag.startMethod != m && ag.entryPoints.contains(m)) {
      ag.entryPoints.remove(m)
      cg.getEntryPoints.remove(m)
    }

    if (ag.methodCount.contains(m)) {
      if (ag.methodCount(m) == ag.size) {
        return
      }
    }
    ag.methodCount.put(m, ag.size)
    ag.instantiatedTypes.addAll(m.delegate.instantiatedTypes)

    val startStatement = m.getControlFlowGraph.getStartPoints.iterator().next()
    implicit val method: SWANMethod = m
    traverseBlock(m.getCFG.blocks(startStatement.asInstanceOf[SWANStatement]).stmts)
  }

  def addCGEdge(from: SWANMethod, to: SWANMethod, stmt: SWANStatement.ApplyFunctionRef, cfgEdge: ControlFlowGraph.Edge): Boolean = {
    val edge = new CallGraph.Edge(stmt, to)
    val b = cg.addEdge(edge)
    if (b) {
      stmt.invokeExpr.updateDeclaredMethod(to.getName, cfgEdge)
      val op = stmt.delegate.instruction match {
        case Instruction.canOperator(op) => op
        case _ => null // never
      }
      if (!debugInfo.contains(op)) {
        debugInfo.put(op, new mutable.HashSet[String]())
      }
      debugInfo(op).add(to.getName)
    }
    b
  }

  def queryRef(stmt: SWANStatement.ApplyFunctionRef, m: SWANMethod)(implicit ag: CGAggregates): Unit = {
    val ref = stmt.getInvokeExpr.asInstanceOf[SWANInvokeExpr].getFunctionRef
    m.getControlFlowGraph.getPredsOf(stmt).forEach(pred => {
      val query = BackwardQuery.make(new ControlFlowGraph.Edge(pred, stmt), ref)
      val solver = new Boomerang(cg, DataFlowScope.INCLUDE_ALL, new DefaultBoomerangOptions)
      val backwardQueryResults = solver.solve(query)
      if (backwardQueryResults.getAllocationSites.isEmpty) {
        fruitlessQueries += 1
      }
      totalQueries += 1
      backwardQueryResults.getAllocationSites.forEach((forwardQuery, _) => {
        val applyStmt = query.asNode().stmt().getTarget.asInstanceOf[SWANStatement.ApplyFunctionRef]
        forwardQuery.`var`().asInstanceOf[AllocVal].getAllocVal match {
          case v: SWANVal.FunctionRef => {
            val target = methods(v.ref)
            if (addCGEdge(m, target, applyStmt, query.cfgEdge())) queriedEdges += 1
            traverseMethod(target)
          }
          case v: SWANVal.DynamicFunctionRef => {
            moduleGroup.ddgs.foreach(ddg => {
              val functionNames = ddg._2.query(v.index, Some(ag.instantiatedTypes))
              functionNames.foreach(name => {
                val target = methods(name)
                if (addCGEdge(m, target, applyStmt, query.cfgEdge())) queriedEdges += 1
                traverseMethod(target)
              })
            })
          }
          case v: SWANVal.BuiltinFunctionRef => {
            if (methods.contains(v.ref)) {
              val target = methods(v.ref)
              if (addCGEdge(m, target, applyStmt, query.cfgEdge())) queriedEdges += 1
              traverseMethod(target)
            }
          }
          case _ => // likely result of partial_apply (ignore for now)
        }
      })
    })
  }

  def buildFromEntryPoints(entryPoints: mutable.LinkedHashSet[SWANMethod]): Unit = {
    val it = entryPoints.iterator
    while (it.hasNext) {
      val m = it.next()
      implicit val ag: CGAggregates = new CGAggregates(cg, entryPoints, m)
      traverseMethod(m)
    }
  }


  // TODO: verify if deterministic (usage of HashSets)
  // TODO: iOS lifecycle
  // TODO: Do separate RTA pass because it is not deterministic currently due
  //  to not only starting at entry point (fake main)
  // can also likely axe all *.__deallocating_deinit, *.deinit functions, and *.modify
  def construct(): (SWANCallGraph, mutable.HashMap[CanOperator, mutable.HashSet[String]], ModuleGroup, Option[(Module, CanModule)]) = {

    Logging.printInfo("Constructing Call Graph")
    val startTime = System.nanoTime()

    def makeMethod(f: CanFunction): SWANMethod = {
      val m = new SWANMethod(f, moduleGroup)
      methods.put(f.name, m)
      m
    }

    // Populate entry points
    mainEntryPoints.addAll(moduleGroup.functions.filter(f => f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))
    otherEntryPoints.addAll(moduleGroup.functions.filter(f => !f.name.startsWith(Constants.fakeMain)).map(f => makeMethod(f)))

    // TODO: cg creates a problem if the entry points call something
    // Eliminate uninteresting functions
    /*val iterator = otherEntryPoints.iterator
    while (iterator.hasNext) {
      val m = iterator.next()
      uninterestingEntryPoints.foreach(s => {
        if (m.getName.endsWith(s)) {
          otherEntryPoints.remove(m)
          methods.remove(m.getName)
        }
      })
    }*/
    // Combine entry points, with the first being the main entry point
    val allEntryPoints = new mutable.LinkedHashSet[SWANMethod]
    allEntryPoints.addAll(mainEntryPoints)
    allEntryPoints.addAll(otherEntryPoints)
    allEntryPoints.filterInPlace(m => !m.getName.startsWith("closure ") && !m.getName.startsWith("reabstraction thunk"))
    allEntryPoints.foreach(e => cg.addEntryPoint(e))
    // Build CG for every entry point
    buildFromEntryPoints(allEntryPoints)

    Logging.printTimeStampSimple(1, startTime, "constructing")
    Logging.printInfo("  Entry Points:  " + cg.getEntryPoints.size().toString)
    Logging.printInfo("  Trivial Edges: " + trivialEdges.toString)
    Logging.printInfo("  Virtual Edges: " + virtualEdges.toString)
    Logging.printInfo("  Queried Edges: " + queriedEdges.toString)
    Logging.printInfo("  Total Queries: " + totalQueries.toString)

    val toTraverse = new mutable.HashSet[String]

    dynamicUIKitModels(toTraverse)
    // dynamicSwiftUIModels(toTraverse) (Not done)
    // Add other dynamic modifications here

    var newValues: Option[(Module, CanModule)] = None

    var retModuleGroup = moduleGroup

    if (modified) {
      Logging.printInfo("Constructing Call Graph with added dynamic models")
      val startTime = System.nanoTime()

      val parser = new SWIRLParser(builder.toString)
      val newRawModule = parser.parseModule()
      val newCanModule = new SWIRLPass().runPasses(newRawModule)
      newCanModule.functions.foreach(f => {
        val m = methods(f.name)
        cg.edgesInto(m).forEach(e => cg.getEdges.remove(e))
        methods.remove(f.name)
        makeMethod(f)
      })
      val newModuleGroup = ModuleGrouper.group(ArrayBuffer(newCanModule), moduleGroup)
      cg.moduleGroup = newModuleGroup
      methods.values.foreach(m => m.moduleGroup = newModuleGroup)
      newValues = Some((newRawModule, newCanModule))
      val entryPoints = new mutable.LinkedHashSet[SWANMethod]()
      methods.values.foreach(m => if (toTraverse.contains(m.getName)) entryPoints.add(m))
      buildFromEntryPoints(entryPoints)
      retModuleGroup = newModuleGroup

      Logging.printInfo("  Entry Points:  " + cg.getEntryPoints.size().toString)
      Logging.printInfo("  Trivial Edges: " + trivialEdges.toString)
      Logging.printInfo("  Virtual Edges: " + virtualEdges.toString)
      Logging.printInfo("  Queried Edges: " + queriedEdges.toString)
      Logging.printInfo("  Total Queries: " + totalQueries.toString)
      Logging.printInfo("  Fruitless Queries: " + fruitlessQueries.toString)
      Logging.printTimeStampSimple(1, startTime, "constructing")
    }

    (cg, debugInfo, retModuleGroup, newValues)
  }

  private def dynamicUIKitModels(toTraverse: mutable.HashSet[String]): Unit = {
    mainEntryPoints.foreach(f => toTraverse.add(f.getName))

    // Gather all delegate types in the main functions.
    // This is _likely_ a sound way of doing it because NSStringFromClass is
    // seemingly always used on the delegate type before its given to
    // UIApplicationMain as the last argument. However, the dataflow between
    // the type creation (via metatype instruction) to the call-site gets
    // complicated for ObjC stuff, so its tricky to use dataflow queries.
    val appDelegateTypes = mutable.HashSet.empty[String]
    methods.filter(_._1.startsWith("main_")).values.foreach(m => {
      m.delegate.blocks.foreach(b => b.operators.foreach(opDef => {
        opDef.operator match {
          case Operator.apply(_, functionRef, arguments) => {
            m.delegate.symbolTable(functionRef.name) match {
              case SymbolTableEntry.operator(_, operator) => {
                operator match {
                  case Operator.functionRef(_, name) if name == "NSStringFromClass" => {
                    m.delegate.symbolTable(arguments(0).name) match {
                      case SymbolTableEntry.operator(_, operator) => {
                        operator match {
                          case Operator.neww(result) => {
                            val regex = "@[^\\s]+ (.*)\\.Type".r
                            val m = regex.findAllIn(result.tpe.name)
                            if (m.nonEmpty) appDelegateTypes.add(m.group(1))
                          }
                          case _ =>
                        }
                      } case _ =>
                    }
                  } case _ =>
                }
              } case _ =>
            }
          } case _ =>
        }
      }))
    })

    if (appDelegateTypes.nonEmpty) {

      // Overwrite __allocating_init() to call init()
      val delegateTypes = mutable.HashMap.empty[SWANMethod, String]
      val visited = mutable.HashSet.empty[String]
      appDelegateTypes.foreach(delegateType => {
        if (!visited.contains(delegateType)) {
          visited.add(delegateType)
          val allocatingInitFunctions = ArrayBuffer.empty[SWANMethod]
          allocatingInitFunctions.appendAll(methods.filter(_._2.getName.contains(delegateType+".__allocating_init()")).values)
          if (allocatingInitFunctions.nonEmpty) {
            allocatingInitFunctions.foreach(allocatingInit => {
              delegateTypes.put(allocatingInit, delegateType)
              val modulePrefix = allocatingInit.getName.substring(0, allocatingInit.getName.indexOf(delegateType))
              val init = methods.find(x => ("^[^\\s]*"+modulePrefix+delegateType+"\\.init\\(\\)").r.findAllIn(x._2.getName).nonEmpty)
              if (init.nonEmpty) {
                builder.openFunction(allocatingInit.delegate, model = true)
                builder.addLine("%1 = new $`" + delegateType + "`")
                builder.addLine("%2 = function_ref @`" + init.get._2.getName + "`, $`Any`")
                builder.addLine("%3 = apply %2(%1), $`" + delegateType + "`")
                builder.addLine("return %3")
                builder.closeFunction()
              } else {
                throw new RuntimeException("Expected init method of delegate type to exist")
              }
            })
          } else {
            throw new RuntimeException("Expected __allocating_init method of delegate type to exist")
          }
        }
      })

      // Overwrite UIApplicationMain stub to call allocating_init and lifecycle methods
      // Each block represents a different AppDelegate (in the case that multiple main
      // functions call UIApplicationMain with their own AppDelegate).
      val m = methods.find(_._1 == "UIApplicationMain").get._2
      builder.openFunction(m.delegate, model = true)
      var i = 4
      val allocatingInitFunctions = ArrayBuffer.from(delegateTypes.keySet)
      allocatingInitFunctions.foreach(allocatingInitFunction => {
        if (allocatingInitFunctions.head != allocatingInitFunction) builder.newBlock()
        val delegateType = delegateTypes(allocatingInitFunction)
        val moduleName = allocatingInitFunction.getName.substring(0, allocatingInitFunction.getName.indexOf(delegateType))
        builder.addLine(s"%$i = function_ref @`"+allocatingInitFunction.getName+"`, $`Any`")
        i += 1
        builder.addLine(s"%$i = apply %4(%3), "+"$`"+delegateType+"`")
        val applyVal = i
        i += 1
        val newValues = new mutable.HashMap[String, String]()
        val applyValues = new ArrayBuffer[(String, ArrayBuffer[String])]()
        methods.values.foreach(m => {
          if (m.getName.contains(moduleName+delegateType)) {
            m.getParameterLocals.forEach(arg => {
              if (arg.getVariableName == "self" && arg.getType.toString.contains("$"+delegateType)) {
                val args = new ArrayBuffer[String]()
                m.getParameterLocals.forEach(a => {
                  if (a != arg) {
                    val tpe = a.getType.asInstanceOf[SWANType].tpe.name
                    if (!newValues.contains(tpe)) {
                      val v = "%"+i
                      i += 1
                      builder.addLine(v+" = new $`"+tpe+"`")
                      newValues.put(tpe, v)
                    }
                    args.append(newValues(tpe))
                  } else {
                    args.append(s"%$applyVal")
                  }
                })
                applyValues.append((m.getName, args))
              }
            })
          }
        })
        applyValues.foreach(v => {
          val v1 = "%"+i
          val v2 = "%"+(i+1)
          i += 2
          builder.addLine(v1+" = function_ref @`"+v._1+"`, $`Any`")
          builder.addLine(v2+" = apply "+v1+"("+v._2.mkString(", ")+"), $`Any`")
        })
        val v = "%"+i
        i += 1
        builder.addLine(v+" = new $`Builtin.Int1`")
        builder.addLine(s"cond_br %$v, true bb${builder.blockNo - 1}, false bb_return")
      })

      builder.addLine("bb_return:", indent = false)
      builder.addLine("%"+i+" = new $`Int32`")
      builder.addLine("return %"+i)
      builder.closeFunction()

      modified = true
    }
  }

  // TODO: WIP Swift UI lifecycle handling
  private def dynamicSwiftUIModels(toTraverse: mutable.HashSet[String]): Unit = {
    mainEntryPoints.foreach(f => toTraverse.add(f.getName))

    methods.foreach(m => {
      if (m._1 == "static Emitron.EmitronApp.$main() -> ()") {
        var tpe: Option[String] = None
        m._2.delegate.blocks.foreach(b => {
          b.operators.foreach(opDef => {
            opDef.operator match {
              case Operator.apply(_, functionRef, arguments) => {
                m._2.delegate.symbolTable(functionRef.name) match {
                  case SymbolTableEntry.operator(_, operator) => {
                    operator match {
                      case Operator.functionRef(_, name) if name == "static (extension in SwiftUI):SwiftUI.App.main() -> ()" => {
                        m._2.delegate.symbolTable(arguments(0).name) match {
                          case SymbolTableEntry.operator(_, operator) => {
                            operator match {
                              case Operator.neww(result) => {
                                val regex = "@[^\\s]+ (.*)\\.Type".r
                                val m = regex.findAllIn(result.tpe.name)
                                if (m.nonEmpty) {
                                  tpe = Some(m.group(1))
                                } else {
                                  throw new RuntimeException("Unexpected SwiftUI app type")
                                }
                              }
                              case _ =>
                            }
                          } case _ =>
                        }
                      } case _ =>
                    }
                  } case _ =>
                }
              } case _ =>
            }
          })
        })
        if (tpe.isEmpty) {
          throw new RuntimeException("Expected to find SwiftUI App type")
        }
        val main = methods.find(_._1 == "static (extension in SwiftUI):SwiftUI.App.main() -> ()").get
        val init = methods.find(x => ("^[^\\s]*"+tpe.get+"\\.init\\(\\)").r.findAllIn(x._2.getName).nonEmpty)

        if (init.nonEmpty) {
          builder.openFunction(main._2.delegate, model = true)
          builder.addLine("%1 = function_ref @`" + init.get._2.getName + "`, $`Any`")
          //builder.addLine("%2 = apply %1(...), $`" + ... + "`")
          //builder.addLine("return %...")
          builder.closeFunction()
        } else {
          throw new RuntimeException("Expected init method of SwiftUI type to exist")
        }
      }
    })
  }
}
