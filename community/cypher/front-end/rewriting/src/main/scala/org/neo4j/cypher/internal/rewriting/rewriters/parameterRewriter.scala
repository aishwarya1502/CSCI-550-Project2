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
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.expressions.AutoExtractedParameter
import org.neo4j.cypher.internal.expressions.ExplicitParameter
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.UnknownSize
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.topDown

/**
 * Rewrites [[Parameter]] types and sizes after planning for better caching.
 */
case object parameterRewriter extends Rewriter {

  private val instance = topDown(Rewriter.lift {
    case p: Parameter =>
      p match {
        case ep: ExplicitParameter =>
          ep.copy(parameterType = CTAny, sizeHint = UnknownSize)(ep.position)
        case aep: AutoExtractedParameter =>
          aep.copy(parameterType = CTAny, sizeHint = UnknownSize)(aep.position)
      }
  })

  override def apply(value: AnyRef): AnyRef = instance(value)
}
