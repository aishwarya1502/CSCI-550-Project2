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

import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.Antlr
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.JavaCc
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsingTestBase
import org.neo4j.exceptions.SyntaxException

class FinishParserTest extends AstParsingTestBase {

  test("FINISH") {
    parsesTo[Clause](
      finish()
    )
  }

  test("UNWIND [1, 2, 3] AS FINISH FINISH") {
    parsesTo[Statement](
      singleQuery(
        unwind(listOfInt(1, 2, 3), varFor("FINISH")),
        finish()
      )
    )
  }

  //  Invalid use of any projection-like items after FINISH

  test("FINISH *") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input '*'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Extraneous input '*': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH *"
          |        ^""".stripMargin
      ))
  }

  test("FINISH a, b") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'a'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Mismatched input 'a': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH a, b"
          |        ^""".stripMargin
      ))
    failsParsing[Statements]
  }

  test("FINISH DISTINCT *") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'DISTINCT'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Mismatched input 'DISTINCT': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH DISTINCT *"
          |        ^""".stripMargin
      ))
  }

  test("FINISH DISTINCT a, b") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'DISTINCT'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Mismatched input 'DISTINCT': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH DISTINCT a, b"
          |        ^""".stripMargin
      ))
  }

  test("FINISH n:A") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'n'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Mismatched input 'n': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH n:A"
          |        ^""".stripMargin
      ))
  }

  test("FINISH n:A&B") {
    failsParsing[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'n'"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
        """Mismatched input 'n': expected ';', <EOF> (line 1, column 8 (offset: 7))
          |"FINISH n:A&B"
          |        ^""".stripMargin
      ))
  }
}
