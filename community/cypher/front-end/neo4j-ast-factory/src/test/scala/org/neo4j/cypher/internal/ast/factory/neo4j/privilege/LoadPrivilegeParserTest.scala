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
package org.neo4j.cypher.internal.ast.factory.neo4j.privilege

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.factory.neo4j.AdministrationAndSchemaCommandParserTestBase
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.Antlr
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.JavaCc
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.exceptions.SyntaxException

class LoadPrivilegeParserTest extends AdministrationAndSchemaCommandParserTestBase {

  Seq(
    ("GRANT", "TO", grantLoadPrivilege: loadPrivilegeFunc),
    ("DENY", "TO", denyLoadPrivilege: loadPrivilegeFunc),
    ("REVOKE GRANT", "FROM", revokeGrantLoadPrivilege: loadPrivilegeFunc),
    ("REVOKE DENY", "FROM", revokeDenyLoadPrivilege: loadPrivilegeFunc),
    ("REVOKE", "FROM", revokeLoadPrivilege: loadPrivilegeFunc)
  ).foreach {
    case (verb: String, preposition: String, func: loadPrivilegeFunc) =>
      Seq[Immutable](true, false).foreach {
        immutable =>
          val immutableString = immutableOrEmpty(immutable)

          test(s"""$verb$immutableString LOAD ON URL "https://my.server.com/some/file.csv" $preposition role""") {
            parsesTo[Statements](func(
              ast.LoadUrlAction,
              ast.LoadUrlQualifier("https://my.server.com/some/file.csv")(InputPosition.NONE),
              Seq(literalRole),
              immutable
            )(pos))
          }

          test(s"""$verb$immutableString LOAD ON CIDR "192.168.1.0/24" $preposition role""") {
            parsesTo[Statements](func(
              ast.LoadCidrAction,
              ast.LoadCidrQualifier("192.168.1.0/24")(InputPosition.NONE),
              Seq(literalRole),
              immutable
            )(pos))
          }

          test(s"""$verb$immutableString LOAD ON URL $$foo $preposition role""") {
            parsesTo[Statements](func(
              ast.LoadUrlAction,
              ast.LoadUrlQualifier(Right(paramFoo))(InputPosition.NONE),
              Seq(literalRole),
              immutable
            )(pos))
          }

          test(s"""$verb$immutableString LOAD ON CIDR $$foo $preposition role""") {
            parsesTo[Statements](func(
              ast.LoadCidrAction,
              ast.LoadCidrQualifier(Right(paramFoo))(InputPosition.NONE),
              Seq(literalRole),
              immutable
            )(pos))
          }

          test(s"""$verb$immutableString LOAD ON ALL DATA $preposition role""") {
            parsesTo[Statements](func(
              ast.LoadAllDataAction,
              ast.LoadAllQualifier()(InputPosition.NONE),
              Seq(literalRole),
              immutable
            )(pos))
          }

      }
  }

  test("""DENY LOAD ON URL "not really a url" TO $role""") {
    parsesTo[Statements](denyLoadPrivilege(
      ast.LoadUrlAction,
      ast.LoadUrlQualifier("not really a url")(InputPosition.NONE),
      Seq(paramRole),
      i = false
    )(pos))
  }

  test("""REVOKE GRANT LOAD ON CIDR 'not a cidr' FROM $role""") {
    parsesTo[Statements](revokeGrantLoadPrivilege(
      ast.LoadCidrAction,
      ast.LoadCidrQualifier("not a cidr")(InputPosition.NONE),
      Seq(paramRole),
      i = false
    )(pos))
  }

  test("GRANT LOAD ON CIDR $x TO `\u0885`, `x\u0885y`") {
    parsesTo[Statements](grantLoadPrivilege(
      ast.LoadCidrAction,
      ast.LoadCidrQualifier(Right(stringParam("x")))(InputPosition.NONE),
      Seq(literalString("\u0885"), literalString("x\u0885y")),
      i = false
    )(pos))
  }

  // Error Cases

  test("GRANT LOAD ON CIDR $x TO \u0885") {
    // the `\u0885` needs to be escaped to be able to be parsed
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input '\u0885': expected a parameter or an identifier"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '\u0885': expected an identifier, '$' (line 1, column 26 (offset: 25))
          |"GRANT LOAD ON CIDR $x TO \u0885"
          |                          ^""".stripMargin
      ))
  }

  test("GRANT LOAD ON CIDR $x TO x\u0885y") {
    // the `\u0885` needs to be escaped to be able to be parsed
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("Invalid input '\u0885': expected \",\" or <EOF>"))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '\u0885': expected ';', <EOF> (line 1, column 27 (offset: 26))
          |"GRANT LOAD ON CIDR $x TO x\u0885y"
          |                           ^""".stripMargin
      ))
  }

  test("""GRANT LOAD ON DATABASE foo TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'DATABASE': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'DATABASE': expected 'CIDR', 'URL', 'ALL' (line 1, column 15 (offset: 14))
          |"GRANT LOAD ON DATABASE foo TO role"
          |               ^""".stripMargin
      ))
  }

  test("""DENY LOAD ON HOME GRAPH TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'HOME': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'HOME': expected 'CIDR', 'URL', 'ALL' (line 1, column 14 (offset: 13))
          |"DENY LOAD ON HOME GRAPH TO role"
          |              ^""".stripMargin
      ))
  }

  test("""REVOKE GRANT LOAD ON DBMS ON ALL DATA FROM role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'DBMS': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'DBMS': expected 'CIDR', 'URL', 'ALL' (line 1, column 22 (offset: 21))
          |"REVOKE GRANT LOAD ON DBMS ON ALL DATA FROM role"
          |                      ^""".stripMargin
      ))
  }

  test("""GRANT LOAD ON CIDR TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'TO': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'TO': expected a string value, '$' (line 1, column 20 (offset: 19))
          |"GRANT LOAD ON CIDR TO role"
          |                    ^""".stripMargin
      ))
  }

  test("""GRANT LOAD ON URL TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'TO': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'TO': expected a string value, '$' (line 1, column 19 (offset: 18))
          |"GRANT LOAD ON URL TO role"
          |                   ^""".stripMargin
      ))
  }

  test("""DENY LOAD TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'TO': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input 'TO': expected 'ON' (line 1, column 11 (offset: 10))
          |"DENY LOAD TO role"
          |           ^""".stripMargin
      ))
  }

  // TODO Loss of helpfulness, in this case the Missing 'TO' error is less helpful, there is a TO later on and adding a TO before URL will not make a valid query
  test("""GRANT LOAD ON CIDR "1.2.3.4/22" URL "https://example.com" TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'URL': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Missing 'TO' at 'URL' (line 1, column 33 (offset: 32))
          |"GRANT LOAD ON CIDR "1.2.3.4/22" URL "https://example.com" TO role"
          |                                 ^""".stripMargin
      ))
  }

  // TODO Loss of helpfulness, in this case the Missing 'TO' error is less helpful, there is a TO later on and adding a TO before URL will not make a valid query
  test("""DENY LOAD ON CIDR "1.2.3.4/22" ON URL "https://example.com" TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input 'ON': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Missing 'TO' at 'ON' (line 1, column 32 (offset: 31))
          |"DENY LOAD ON CIDR "1.2.3.4/22" ON URL "https://example.com" TO role"
          |                                ^""".stripMargin
      ))
  }

  test("""GRANT LOAD ON URL "https://www.badger.com","file:///test.csv" TO role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input ',': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input ',': expected 'TO' (line 1, column 43 (offset: 42))
          |"GRANT LOAD ON URL "https://www.badger.com","file:///test.csv" TO role"
          |                                           ^""".stripMargin
      ))
  }

  test("""REVOKE DENY LOAD ON CIDR "1.2.3.4/22","1.2.3.4/22" FROM role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input ',': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input ',': expected 'FROM' (line 1, column 38 (offset: 37))
          |"REVOKE DENY LOAD ON CIDR "1.2.3.4/22","1.2.3.4/22" FROM role"
          |                                      ^""".stripMargin
      ))
  }

  test("""REVOKE DENY LOAD ON ALL DATA "1.2.3.4/22" FROM role""") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '1.2.3.4/22': expected"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Extraneous input '"1.2.3.4/22"': expected 'FROM' (line 1, column 30 (offset: 29))
          |"REVOKE DENY LOAD ON ALL DATA "1.2.3.4/22" FROM role"
          |                              ^""".stripMargin
      ))
  }

  test("GRANT LOAD ON CIDR 'x'+'y' TO role") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '+': expected "TO"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Mismatched input '+': expected 'TO' (line 1, column 23 (offset: 22))
          |"GRANT LOAD ON CIDR 'x'+'y' TO role"
          |                       ^""".stripMargin
      ))
  }

  test("GRANT LOAD ON URL ['x'] TO role") {
    testName should notParse[Statements]
      .parseIn(JavaCc)(_.withMessageStart("""Invalid input '[': expected "\"", "\'" or a parameter"""))
      .parseIn(Antlr)(_.throws[SyntaxException].withMessage(
        """Extraneous input '[': expected a string value, '$' (line 1, column 19 (offset: 18))
          |"GRANT LOAD ON URL ['x'] TO role"
          |                   ^""".stripMargin
      ))
  }

  // help methods

  def grantLoadPrivilege(
    a: ast.LoadActions,
    q: ast.LoadPrivilegeQualifier,
    r: Seq[Expression],
    i: Immutable
  ): InputPosition => ast.Statement =
    ast.GrantPrivilege(
      ast.LoadPrivilege(a)(InputPosition.NONE),
      i,
      Some(ast.FileResource()(InputPosition.NONE)),
      List(q),
      r
    )

  def denyLoadPrivilege(
    a: ast.LoadActions,
    q: ast.LoadPrivilegeQualifier,
    r: Seq[Expression],
    i: Immutable
  ): InputPosition => ast.Statement =
    ast.DenyPrivilege(
      ast.LoadPrivilege(a)(InputPosition.NONE),
      i,
      Some(ast.FileResource()(InputPosition.NONE)),
      List(q),
      r
    )

  def revokeGrantLoadPrivilege(
    a: ast.LoadActions,
    q: ast.LoadPrivilegeQualifier,
    r: Seq[Expression],
    i: Immutable
  ): InputPosition => ast.Statement =
    ast.RevokePrivilege(
      ast.LoadPrivilege(a)(InputPosition.NONE),
      i,
      Some(ast.FileResource()(InputPosition.NONE)),
      List(q),
      r,
      ast.RevokeGrantType()(pos)
    )

  def revokeDenyLoadPrivilege(
    a: ast.LoadActions,
    q: ast.LoadPrivilegeQualifier,
    r: Seq[Expression],
    i: Immutable
  ): InputPosition => ast.Statement =
    ast.RevokePrivilege(
      ast.LoadPrivilege(a)(InputPosition.NONE),
      i,
      Some(ast.FileResource()(InputPosition.NONE)),
      List(q),
      r,
      ast.RevokeDenyType()(pos)
    )

  def revokeLoadPrivilege(
    a: ast.LoadActions,
    q: ast.LoadPrivilegeQualifier,
    r: Seq[Expression],
    i: Immutable
  ): InputPosition => ast.Statement =
    ast.RevokePrivilege(
      ast.LoadPrivilege(a)(InputPosition.NONE),
      i,
      Some(ast.FileResource()(InputPosition.NONE)),
      List(q),
      r,
      ast.RevokeBothType()(pos)
    )
}
