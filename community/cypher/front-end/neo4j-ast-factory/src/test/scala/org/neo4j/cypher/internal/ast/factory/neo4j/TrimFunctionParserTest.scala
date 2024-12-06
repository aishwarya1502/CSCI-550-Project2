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
import org.neo4j.cypher.internal.expressions.Expression

class TrimFunctionParserTest extends AstParsingTestBase {

  // TRIM defaults to BOTH and no characterString (characterString will be WHITESPACE later)
  test("trim('hello')") {
    parsesTo[Expression](function("trim", literalString("BOTH"), literalString("hello")))
  }

  // All trim spec keywords parse as expected
  Seq("BOTH", "LEADING", "TRAILING").foreach { trimSpec =>
    test(s"trim($trimSpec ' ' FROM \"hello\")") {
      parsesTo[Expression](function("trim", literalString(trimSpec), literalString(" "), literalString("hello")))
    }
  }

  // All trim spec keywords parse as expected
  Seq("BOTH", "LEADING", "TRAILING").foreach { trimSpec =>
    test(s"trim($trimSpec FROM \"hello\")") {
      parsesTo[Expression](function("trim", literalString(trimSpec), literalString("hello")))
    }
  }

  test(s"trim(' ' FROM \"hello\")") {
    parsesTo[Expression](function("trim", literalString("BOTH"), literalString(" "), literalString("hello")))
  }

  test(s"trim(' ' FROM replace(\"xhellox\", \"x\", \" \"))") {
    parsesTo[Expression](
      function(
        "trim",
        literalString("BOTH"),
        literalString(" "),
        function("replace", literalString("xhellox"), literalString("x"), literalString(" "))
      )
    )
  }

  test(s"trim(FROM \"hello\")") {
    parsesTo[Expression](function("trim", literalString("BOTH"), literalString("hello")))
  }

  test("trim(s)") {
    parsesTo[Expression](function("trim", literalString("BOTH"), varFor("s")))
  }

  test("trim(n.prop)") {
    parsesTo[Expression](function("trim", literalString("BOTH"), prop("n", "prop")))
  }

  Seq("BOTH", "LEADING", "TRAILING").foreach { trimSpec =>
    test(s"trim($trimSpec n.prop1 + n.prop2 FROM n.prop3)") {
      parsesTo[Expression](function(
        "trim",
        literalString(trimSpec),
        add(prop("n", "prop1"), prop("n", "prop2")),
        prop("n", "prop3")
      ))
    }
  }

  test("trim(FROM from)") {
    parsesTo[Expression](function("trim", literalString("BOTH"), varFor("from")))
  }

  // Failing tests
  test("RETURN trim(' ' \"hello\")") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'hello'"))
      .parseIn(Antlr)(_.withMessage(
        """No viable alternative: expected an expression (line 1, column 17 (offset: 16))
          |"RETURN trim(' ' "hello")"
          |                 ^""".stripMargin
      ))
  }

  Seq("BOTH", "LEADING", "TRAILING").foreach { trimSpec =>
    test(s"RETURN trim($trimSpec \"hello\")") {
      failsParsing[Statements]
        .parseIn(JavaCc)(_.withMessageStart("Invalid input ')'"))
        .parseIn(Antlr)(_.withMessageStart(
          "Mismatched input ')': expected '.', ':', 'IS', '[', an expression, '=~', 'STARTS', 'ENDS', 'CONTAINS', 'IN', '::', 'FROM'"
        ))
    }
  }

  Seq("BOTH", "LEADING", "TRAILING").foreach { trimSpec =>
    test(s"RETURN trim($trimSpec ' ' \"hello\")") {
      failsParsing[Statements]
        .parseIn(JavaCc)(_.withMessageStart("Invalid input 'hello'"))
        .parseIn(Antlr)(_.withMessageStart("Missing 'FROM' at '\"hello\"'"))
    }
  }
}
