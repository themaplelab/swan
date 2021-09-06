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

package ca.ualberta.maple.swan.spds.analysis.boomerang.solver

import ca.ualberta.maple.swan.spds.analysis.boomerang.BoomerangOptions
import ca.ualberta.maple.swan.spds.analysis.boomerang.scene.{Field, Statement}
import ca.ualberta.maple.swan.spds.analysis.boomerang.staticfields._
import ca.ualberta.maple.swan.spds.analysis.wpds.impl.Weight

import scala.collection.mutable

class Strategies[W <: Weight](opts: BoomerangOptions,
                              solver: AbstractBoomerangSolver[W],
                              fieldLoadStatements: mutable.MultiDict[Field, Statement],
                              fieldStoreStatements: mutable.MultiDict[Field, Statement]) {

  val staticFieldStrategy: StaticFieldStrategy[W] = opts.getStaticFieldStrategy match {
    case BoomerangOptions.FLOW_SENSITIVE => new FlowSensitiveStaticFieldStrategy
    case BoomerangOptions.IGNORE => new IgnoreStaticFieldStrategy
    case BoomerangOptions.SINGLETON => new SingletonStaticFieldStrategy(solver, fieldLoadStatements, fieldStoreStatements)
  }
}
