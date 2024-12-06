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
package org.neo4j.cypher.internal.ast.factory.neo4j

import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.Antlr
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.JavaCc
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.exceptions.SyntaxException

class FunctionInvocationParserTest extends AstParsingTestBase {

  test("foo()") {
    parsesTo[FunctionInvocation](function("foo"))
  }

  test("foo('test', 1 + 2)") {
    parsesTo[FunctionInvocation](function("foo", literalString("test"), add(literalInt(1), literalInt(2))))
  }

  test("my.namespace.foo()") {
    parsesTo[FunctionInvocation](function(List("my", "namespace"), "foo"))
  }

  test("my.namespace.foo('test', 1 + 2)") {
    parsesTo[FunctionInvocation](function(
      List("my", "namespace"),
      "foo",
      literalString("test"),
      add(literalInt(1), literalInt(2))
    ))
  }

  test("sum(DISTINCT foo)") {
    parsesTo[FunctionInvocation](distinctFunction("sum", varFor("foo")))
  }

  test("sum(ALL foo)") {
    parsesTo[FunctionInvocation](function("sum", varFor("foo")))
  }

  test("function parameters without comma separation should not parse") {
    "return foo('test' 42)" should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input '42': expected"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Extraneous input '42': expected ',', ')' (line 1, column 19 (offset: 18))
          |"return foo('test' 42)"
          |                   ^""".stripMargin
      ))
  }

  test("function parameters with invalid start comma should not parse") {
    "return foo(, 'test', 42)" should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input ',': expected"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '(': expected ';', <EOF> (line 1, column 11 (offset: 10))
          |"return foo(, 'test', 42)"
          |           ^""".stripMargin
      ))
  }
}
