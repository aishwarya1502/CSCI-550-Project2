/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
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
 */
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticPatternCheck
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasMappableExpressions
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.LogicalProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTNode

sealed trait RemoveItem extends ASTNode with SemanticCheckable with HasMappableExpressions[RemoveItem]

case class RemoveLabelItem(variable: LogicalVariable, labels: Seq[LabelName], containsIs: Boolean)(
  val position: InputPosition
) extends RemoveItem {

  override def semanticCheck: SemanticCheck =
    SemanticExpressionCheck.simple(variable) chain
      SemanticPatternCheck.checkValidLabels(labels, position) chain
      SemanticExpressionCheck.expectType(CTNode.covariant, variable)

  override def mapExpressions(f: Expression => Expression): RemoveItem = copy(
    f(variable).asInstanceOf[LogicalVariable]
  )(this.position)
}

case class RemovePropertyItem(property: LogicalProperty) extends RemoveItem {
  override def position: InputPosition = property.position

  override def semanticCheck: SemanticCheck = SemanticExpressionCheck.simple(property) chain
    SemanticPatternCheck.checkValidPropertyKeyNames(Seq(property.propertyKey))

  override def mapExpressions(f: Expression => Expression): RemoveItem =
    property match {
      case Property(map, propertyKey) =>
        copy(Property(f(map), propertyKey)(property.position))
      case _ => throw new IllegalStateException(
          s"We don't expect this to be called on any other logical properties. Got: $property"
        )
    }
}
