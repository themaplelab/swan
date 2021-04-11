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

import boomerang.scene.{IfStatement, Statement, Val}

class SWANIfStatement(val stmt: SWANStatement.ConditionalBranch) extends IfStatement {

  override def getTarget: Statement = null
  override def evaluate(v: Val): IfStatement.Evaluation = null
  override def uses(v: Val): Boolean = stmt.getRightOp.equals(v)
  override def toString: String = "<if_stmt>" + stmt + "</if_stmt>"
}
