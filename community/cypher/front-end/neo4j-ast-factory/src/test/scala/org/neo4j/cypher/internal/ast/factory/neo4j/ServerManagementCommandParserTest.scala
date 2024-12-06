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

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.NoOptions
import org.neo4j.cypher.internal.ast.OptionsMap
import org.neo4j.cypher.internal.ast.OptionsParam
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.Antlr
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.JavaCc
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTMap
import org.neo4j.exceptions.SyntaxException

class ServerManagementCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {
  // SHOW

  test("SHOW SERVERS") {
    assertAst(ast.ShowServers(None)(defaultPos))
  }

  test("SHOW SERVER") {
    assertAst(ast.ShowServers(None)(defaultPos))
  }

  test("SHOW SERVERS YIELD *") {
    val yieldOrWhere = Left((yieldClause(returnAllItems), None))
    assertAst(ast.ShowServers(Some(yieldOrWhere))(defaultPos))
  }

  test("SHOW SERVER YIELD *") {
    val yieldOrWhere = Left((yieldClause(returnAllItems), None))
    assertAst(ast.ShowServers(Some(yieldOrWhere))(defaultPos))
  }

  test("SHOW SERVERS YIELD address") {
    val columns = yieldClause(returnItems(variableReturnItem("address")), None)
    val yieldOrWhere = Some(Left((columns, None)))
    assertAst(ast.ShowServers(yieldOrWhere)(defaultPos))
  }

  test("SHOW SERVERS YIELD address ORDER BY name") {
    val orderByClause = Some(orderBy(sortItem(varFor("name"))))
    val columns = yieldClause(returnItems(variableReturnItem("address")), orderByClause)
    val yieldOrWhere = Some(Left((columns, None)))
    assertAst(ast.ShowServers(yieldOrWhere)(defaultPos))
  }

  test("SHOW SERVERS YIELD address ORDER BY name SKIP 1 LIMIT 2 WHERE name = 'badger' RETURN *") {
    val orderByClause = orderBy(sortItem(varFor("name")))
    val whereClause = where(equals(varFor("name"), literalString("badger")))
    val columns = yieldClause(
      returnItems(variableReturnItem("address")),
      Some(orderByClause),
      Some(skip(1)),
      Some(limit(2)),
      Some(whereClause)
    )
    val yieldOrWhere = Some(Left((columns, Some(returnAll))))
    assertAst(ast.ShowServers(yieldOrWhere)(defaultPos))
  }

  test("SHOW SERVERS YIELD * RETURN id") {
    val yieldOrWhere: Left[(Yield, Some[Return]), Nothing] =
      Left((yieldClause(returnAllItems), Some(return_(variableReturnItem("id")))))
    assertAst(ast.ShowServers(Some(yieldOrWhere))(defaultPos))
  }

  test("SHOW SERVERS WHERE name = 'badger'") {
    val yieldOrWhere = Right(where(equals(varFor("name"), literalString("badger"))))
    assertAst(ast.ShowServers(Some(yieldOrWhere))(defaultPos))
  }

  test("SHOW SERVER WHERE name = 'badger'") {
    val yieldOrWhere = Right(where(equals(varFor("name"), literalString("badger"))))
    assertAst(ast.ShowServers(Some(yieldOrWhere))(defaultPos))
  }

  // TODO Check message, potential loss of information
  test("SHOW SERVERS RETURN *") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        "Invalid input 'RETURN': expected \"WHERE\", \"YIELD\" or <EOF> (line 1, column 14 (offset: 13))"
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'RETURN': expected ';', <EOF> (line 1, column 14 (offset: 13))
          |"SHOW SERVERS RETURN *"
          |              ^""".stripMargin
      ))
  }

  // TODO Check message, potential loss of information
  test("SHOW SERVERS 'name'") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        "Invalid input 'name': expected \"WHERE\", \"YIELD\" or <EOF> (line 1, column 14 (offset: 13))"
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Extraneous input ''name'': expected ';', <EOF> (line 1, column 14 (offset: 13))
          |"SHOW SERVERS 'name'"
          |              ^""".stripMargin
      ))
  }

  // TODO Check message, potential loss of information
  test("SHOW SERVER 'name'") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        "Invalid input 'name': expected \"WHERE\", \"YIELD\" or <EOF> (line 1, column 13 (offset: 12))"
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Extraneous input ''name'': expected ';', <EOF> (line 1, column 13 (offset: 12))
          |"SHOW SERVER 'name'"
          |             ^""".stripMargin
      ))
  }

  // ENABLE

  test("ENABLE SERVER 'name'") {
    assertAst(ast.EnableServer(literal("name"), NoOptions)(defaultPos))
  }

  test("ENABLE SERVER $name OPTIONS { tags: ['snake', 'flower'] }") {
    val listLiteral = ListLiteral(List(literalString("snake"), literalString("flower")))(InputPosition(36, 1, 37))
    val optionsMap = OptionsMap(Map("tags" -> listLiteral))
    assertAst(ast.EnableServer(Right(stringParam("name")), optionsMap)(defaultPos))
  }

  test("ENABLE SERVER 'name' OPTIONS { modeConstraint: $mode }") {
    val optionsMap = OptionsMap(Map("modeConstraint" -> parameter("mode", CTAny)))
    assertAst(ast.EnableServer(literal("name"), optionsMap)(defaultPos))
  }

  test("ENABLE SERVER 'name' OPTIONS $op") {
    val optionsParam = OptionsParam(parameter("op", CTMap))
    assertAst(ast.EnableServer(literal("name"), optionsParam)(defaultPos))
  }

  test("ENABLE SERVER name") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'name': expected "\"", "\'" or a parameter"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'name': expected a string value, '$' (line 1, column 15 (offset: 14))
          |"ENABLE SERVER name"
          |               ^""".stripMargin
      ))
  }

  test("ENABLE SERVER") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '': expected "\"", "\'" or a parameter"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '': expected a string value, '$' (line 1, column 14 (offset: 13))
          |"ENABLE SERVER"
          |              ^""".stripMargin
      ))
  }

  // ALTER

  test("ALTER SERVER 'name' SET OPTIONS { modeConstraint: 'PRIMARY'}") {
    val optionsMap = OptionsMap(Map("modeConstraint" -> literalString("PRIMARY")))
    assertAst(ast.AlterServer(literal("name"), optionsMap)(defaultPos))
  }

  test("ALTER SERVER $name SET OPTIONS {}") {
    val optionsMap = OptionsMap(Map.empty)
    assertAst(ast.AlterServer(Right(stringParam("name")), optionsMap)(defaultPos))
  }

  test("ALTER SERVER 'name' SET OPTIONS $map") {
    assertAst(ast.AlterServer(literal("name"), OptionsParam(parameter("map", CTMap)))(defaultPos))
  }

  test("ALTER SERVER 'name'") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '': expected "SET"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '': expected 'SET' (line 1, column 20 (offset: 19))
          |"ALTER SERVER 'name'"
          |                    ^""".stripMargin
      ))
  }

  test("ALTER SERVER 'name' SET OPTIONS") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '': expected "{" or a parameter"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '': expected '{', '$' (line 1, column 32 (offset: 31))
          |"ALTER SERVER 'name' SET OPTIONS"
          |                                ^""".stripMargin
      ))
  }

  // RENAME

  test("RENAME SERVER 'badger' TO 'snake'") {
    assertAst(ast.RenameServer(literal("badger"), literal("snake"))(defaultPos))
  }

  test("RENAME SERVER $from TO $to") {
    assertAst(ast.RenameServer(Right(stringParam("from")), Right(stringParam("to")))(defaultPos))
  }

  test("RENAME SERVER `bad,ger` TO $to") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'bad,ger': expected "\"", "\'" or a parameter"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '`bad,ger`': expected a string value, '$' (line 1, column 15 (offset: 14))
          |"RENAME SERVER `bad,ger` TO $to"
          |               ^""".stripMargin
      ))
  }

  test("RENAME SERVER 'badger' $to") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input '$': expected \"TO\""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Extraneous input '$': expected 'TO' (line 1, column 24 (offset: 23))
          |"RENAME SERVER 'badger' $to"
          |                        ^""".stripMargin
      ))
  }

  // DROP

  test("DROP SERVER 'name'") {
    assertAst(ast.DropServer(literal("name"))(defaultPos))
  }

  test("DROP SERVER $name") {
    assertAst(ast.DropServer(Right(stringParam("name")))(defaultPos))
  }

  test("DROP SERVER name") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        """Invalid input 'name': expected "\"", "\'" or a parameter (line 1, column 13 (offset: 12))"""
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'name': expected a string value, '$' (line 1, column 13 (offset: 12))
          |"DROP SERVER name"
          |             ^""".stripMargin
      ))
  }

  test("DROP SERVER") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        """Invalid input '': expected "\"", "\'" or a parameter (line 1, column 12 (offset: 11))"""
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '': expected a string value, '$' (line 1, column 12 (offset: 11))
          |"DROP SERVER"
          |            ^""".stripMargin
      ))
  }

  // DEALLOCATE

  test("DEALLOCATE DATABASES FROM SERVER 'badger', 'snake'") {
    assertAst(ast.DeallocateServers(dryRun = false, Seq(literal("badger"), literal("snake")))(defaultPos))
  }

  test("DRYRUN DEALLOCATE DATABASES FROM SERVER 'badger', 'snake'") {
    assertAst(
      ast.DeallocateServers(dryRun = true, Seq(literal("badger"), literal("snake")))(InputPosition(7, 1, 8))
    )
  }

  test("DEALLOCATE DATABASES FROM SERVER $name") {
    assertAst(ast.DeallocateServers(dryRun = false, Seq(Right(stringParam("name"))))(defaultPos))
  }

  test("DEALLOCATE DATABASE FROM SERVERS $name, 'foo'") {
    assertAst(ast.DeallocateServers(dryRun = false, Seq(Right(stringParam("name")), literal("foo")))(defaultPos))
  }

  test("DEALLOCATE SERVERS $name, 'foo'") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input 'SERVERS': expected \"DATABASE\" or \"DATABASES\""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'SERVERS': expected 'DATABASE', 'DATABASES' (line 1, column 12 (offset: 11))
          |"DEALLOCATE SERVERS $name, 'foo'"
          |            ^""".stripMargin
      ))
  }

  test("REALLOCATE DATABASE") {
    assertAst(ast.ReallocateDatabases(dryRun = false)(defaultPos))
  }

  test("REALLOCATE DATABASES") {
    assertAst(ast.ReallocateDatabases(dryRun = false)(defaultPos))
  }

  test("DRYRUN REALLOCATE DATABASES") {
    assertAst(ast.ReallocateDatabases(dryRun = true)(InputPosition(7, 1, 8)))
  }

  test("REALLOCATE SERVERS") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart(
        "Invalid input 'SERVERS': expected \"DATABASE\" or \"DATABASES\" (line 1, column 12 (offset: 11))"
      ))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'SERVERS': expected 'DATABASE', 'DATABASES' (line 1, column 12 (offset: 11))
          |"REALLOCATE SERVERS"
          |            ^""".stripMargin
      ))
  }
}
