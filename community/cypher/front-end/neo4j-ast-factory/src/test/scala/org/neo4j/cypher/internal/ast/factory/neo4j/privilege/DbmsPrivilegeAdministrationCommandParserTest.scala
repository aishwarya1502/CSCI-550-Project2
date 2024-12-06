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

import org.neo4j.cypher.internal.ast.AllAliasManagementActions
import org.neo4j.cypher.internal.ast.AllDatabaseManagementActions
import org.neo4j.cypher.internal.ast.AllDbmsAction
import org.neo4j.cypher.internal.ast.AllPrivilegeActions
import org.neo4j.cypher.internal.ast.AllRoleActions
import org.neo4j.cypher.internal.ast.AllUserActions
import org.neo4j.cypher.internal.ast.AlterAliasAction
import org.neo4j.cypher.internal.ast.AlterDatabaseAction
import org.neo4j.cypher.internal.ast.AlterUserAction
import org.neo4j.cypher.internal.ast.AssignPrivilegeAction
import org.neo4j.cypher.internal.ast.AssignRoleAction
import org.neo4j.cypher.internal.ast.CompositeDatabaseManagementActions
import org.neo4j.cypher.internal.ast.CreateAliasAction
import org.neo4j.cypher.internal.ast.CreateCompositeDatabaseAction
import org.neo4j.cypher.internal.ast.CreateDatabaseAction
import org.neo4j.cypher.internal.ast.CreateRoleAction
import org.neo4j.cypher.internal.ast.CreateUserAction
import org.neo4j.cypher.internal.ast.DbmsAction
import org.neo4j.cypher.internal.ast.DropAliasAction
import org.neo4j.cypher.internal.ast.DropCompositeDatabaseAction
import org.neo4j.cypher.internal.ast.DropDatabaseAction
import org.neo4j.cypher.internal.ast.DropRoleAction
import org.neo4j.cypher.internal.ast.DropUserAction
import org.neo4j.cypher.internal.ast.RemovePrivilegeAction
import org.neo4j.cypher.internal.ast.RemoveRoleAction
import org.neo4j.cypher.internal.ast.RenameRoleAction
import org.neo4j.cypher.internal.ast.RenameUserAction
import org.neo4j.cypher.internal.ast.ServerManagementAction
import org.neo4j.cypher.internal.ast.SetDatabaseAccessAction
import org.neo4j.cypher.internal.ast.SetPasswordsAction
import org.neo4j.cypher.internal.ast.SetUserHomeDatabaseAction
import org.neo4j.cypher.internal.ast.SetUserStatusAction
import org.neo4j.cypher.internal.ast.ShowAliasAction
import org.neo4j.cypher.internal.ast.ShowPrivilegeAction
import org.neo4j.cypher.internal.ast.ShowRoleAction
import org.neo4j.cypher.internal.ast.ShowServerAction
import org.neo4j.cypher.internal.ast.ShowUserAction
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.factory.neo4j.AdministrationAndSchemaCommandParserTestBase
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.Antlr
import org.neo4j.cypher.internal.ast.factory.neo4j.test.util.AstParsing.JavaCc
import org.neo4j.exceptions.SyntaxException

class DbmsPrivilegeAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  // Impersonate and execute privileges have their own files and are not in this list

  def privilegeTests(command: String, preposition: String, privilegeFunc: dbmsPrivilegeFunc): Unit = {
    Seq[Immutable](true, false).foreach {
      immutable =>
        val immutableString = immutableOrEmpty(immutable)
        val offset = command.length + immutableString.length + 1
        Seq(
          ("CREATE ROLE", CreateRoleAction),
          ("RENAME ROLE", RenameRoleAction),
          ("DROP ROLE", DropRoleAction),
          ("SHOW ROLE", ShowRoleAction),
          ("ASSIGN ROLE", AssignRoleAction),
          ("REMOVE ROLE", RemoveRoleAction),
          ("ROLE MANAGEMENT", AllRoleActions),
          ("CREATE USER", CreateUserAction),
          ("RENAME USER", RenameUserAction),
          ("DROP USER", DropUserAction),
          ("SHOW USER", ShowUserAction),
          ("SET PASSWORD", SetPasswordsAction),
          ("SET PASSWORDS", SetPasswordsAction),
          ("SET USER STATUS", SetUserStatusAction),
          ("SET USER HOME DATABASE", SetUserHomeDatabaseAction),
          ("ALTER USER", AlterUserAction),
          ("USER MANAGEMENT", AllUserActions),
          ("CREATE DATABASE", CreateDatabaseAction),
          ("DROP DATABASE", DropDatabaseAction),
          ("ALTER DATABASE", AlterDatabaseAction),
          ("SET DATABASE ACCESS", SetDatabaseAccessAction),
          ("DATABASE MANAGEMENT", AllDatabaseManagementActions),
          ("SHOW PRIVILEGE", ShowPrivilegeAction),
          ("ASSIGN PRIVILEGE", AssignPrivilegeAction),
          ("REMOVE PRIVILEGE", RemovePrivilegeAction),
          ("PRIVILEGE MANAGEMENT", AllPrivilegeActions),
          ("SHOW SERVER", ShowServerAction),
          ("SHOW SERVERS", ShowServerAction),
          ("SERVER MANAGEMENT", ServerManagementAction),
          ("COMPOSITE DATABASE MANAGEMENT", CompositeDatabaseManagementActions),
          ("CREATE COMPOSITE DATABASE", CreateCompositeDatabaseAction),
          ("DROP COMPOSITE DATABASE", DropCompositeDatabaseAction),
          ("ALIAS MANAGEMENT", AllAliasManagementActions),
          ("CREATE ALIAS", CreateAliasAction),
          ("DROP ALIAS", DropAliasAction),
          ("ALTER ALIAS", AlterAliasAction),
          ("SHOW ALIAS", ShowAliasAction)
        ).foreach {
          case (privilege: String, action: DbmsAction) =>
            test(s"$command$immutableString $privilege ON DBMS $preposition role") {
              parsesTo[Statements](privilegeFunc(action, Seq(literalRole), immutable)(pos))
            }

            test(s"$command$immutableString $privilege ON DBMS $preposition role1, $$role2") {
              parsesTo[Statements](privilegeFunc(action, Seq(literalRole1, paramRole2), immutable)(pos))
            }

            test(s"$command$immutableString $privilege ON DBMS $preposition `r:ole`") {
              parsesTo[Statements](privilegeFunc(action, Seq(literalRColonOle), immutable)(pos))
            }

            test(s"$command$immutableString $privilege ON DATABASE $preposition role") {
              val offset = command.length + immutableString.length + 5 + privilege.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input 'DATABASE': expected "DBMS" (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  """Mismatched input 'DATABASE': expected 'DBMS'"""
                ))
            }

            test(s"$command$immutableString $privilege ON HOME DATABASE $preposition role") {
              val offset = command.length + immutableString.length + 5 + privilege.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input 'HOME': expected "DBMS" (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  """Mismatched input 'HOME': expected 'DBMS'"""
                ))
            }

            test(s"$command$immutableString $privilege DBMS $preposition role") {
              val offset = command.length + immutableString.length + 2 + privilege.length
              val expected = (command, immutable, privilege) match {
                // this case looks like granting/revoking a role named MANAGEMENT to/from a user
                case ("GRANT", false, "ROLE MANAGEMENT") | ("REVOKE", false, "ROLE MANAGEMENT") =>
                  s"""Invalid input 'DBMS': expected "," or "$preposition" (line 1, column ${offset + 1} (offset: $offset))"""
                case _ => s"""Invalid input 'DBMS': expected "ON" (line 1, column ${offset + 1} (offset: $offset))"""
              }

              val antlrExpected = (command, immutable, privilege) match {
                // this case looks like granting/revoking a role named MANAGEMENT to/from a user
                // TODO Loss of information
                case ("GRANT", false, "ROLE MANAGEMENT") | ("REVOKE", false, "ROLE MANAGEMENT") =>
                  "No viable alternative"
                case _ => s"Missing 'ON' at 'DBMS' (line 1, column ${offset + 1} (offset: $offset))"
              }
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(expected))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(antlrExpected))
            }

            test(s"$command$immutableString $privilege ON $preposition role") {
              val offset = command.length + immutableString.length + 5 + privilege.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input '$preposition': expected "DBMS" (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  s"""Missing 'DBMS' at '$preposition' (line 1, column ${offset + 1} (offset: $offset))"""
                ))
            }

            // TODO missing comma
            test(s"$command$immutableString $privilege ON DBMS $preposition r:ole") {
              val offset = command.length + immutableString.length + 12 + privilege.length + preposition.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input ':': expected "," or <EOF> (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  s"""Mismatched input ':': expected ';', <EOF> (line 1, column ${offset + 1} (offset: $offset))"""
                ))
            }

            test(s"$command$immutableString $privilege ON DBMS $preposition") {
              val offset = command.length + immutableString.length + 10 + privilege.length + preposition.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input '': expected a parameter or an identifier (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  s"""Mismatched input '': expected an identifier, '$$' (line 1, column ${offset + 1} (offset: $offset))"""
                ))
            }

            test(s"$command$immutableString $privilege ON DBMS") {
              val offset = command.length + immutableString.length + 9 + privilege.length
              testName should notParse[Statements]
                .parseIn(JavaCc)(_.withMessageStart(
                  s"""Invalid input '': expected "$preposition" (line 1, column ${offset + 1} (offset: $offset))"""
                ))
                .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
                  s"""Mismatched input '': expected '$preposition' (line 1, column ${offset + 1} (offset: $offset))"""
                ))
            }
        }

        // The tests below needs to be outside the loop since ALL [PRIVILEGES] ON DATABASE is a valid (but different) command

        test(s"$command$immutableString ALL ON DBMS $preposition $$role") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(paramRole), immutable)(pos))
        }

        test(s"$command$immutableString ALL ON DBMS $preposition role1, role2") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(literalRole1, literalRole2), immutable)(pos))
        }

        test(s"$command$immutableString ALL PRIVILEGES ON DBMS $preposition role") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(literalRole), immutable)(pos))
        }

        test(s"$command$immutableString ALL PRIVILEGES ON DBMS $preposition $$role1, role2") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(paramRole1, literalRole2), immutable)(pos))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DBMS $preposition role") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(literalRole), immutable)(pos))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DBMS $preposition `r:ole`, $$role2") {
          parsesTo[Statements](privilegeFunc(AllDbmsAction, Seq(literalRColonOle, paramRole2), immutable)(pos))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DATABASE $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'DATABASE': expected "DBMS" (line 1, column ${offset + 24} (offset: ${offset + 23}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Invalid input 'DATABASE': expected "DBMS" (line 1, column ${offset + 24} (offset: ${offset + 23}))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON HOME DATABASE $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'HOME': expected "DBMS" (line 1, column ${offset + 24} (offset: ${offset + 23}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Invalid input 'HOME': expected "DBMS" (line 1, column ${offset + 24} (offset: ${offset + 23}))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'DBMS': expected "ON" (line 1, column ${offset + 21} (offset: ${offset + 20}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Missing 'ON' at 'DBMS' (line 1, column ${offset + 21} (offset: ${offset + 20}))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES $preposition") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input '$preposition': expected "ON" (line 1, column ${offset + 21} (offset: ${offset + 20}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Mismatched input '$preposition': expected 'ON' (line 1, column ${offset + 21} (offset: ${offset + 20}))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON $preposition") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input '$preposition': expected
                 |  "DATABASE"
                 |  "DATABASES"
                 |  "DBMS"
                 |  "DEFAULT"
                 |  "GRAPH"
                 |  "GRAPHS"
                 |  "HOME" (line 1, column ${offset + 24} (offset: ${offset + 23}))""".stripMargin
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Mismatched input '$preposition': expected 'DEFAULT', 'HOME', 'DATABASE', 'DATABASES', 'GRAPH', 'GRAPHS', 'DBMS' (line 1, column ${offset + 24} (offset: ${offset + 23}))"""
            ))
        }

        // TODO Missing comma in messsage
        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DBMS $preposition r:ole") {
          val finalOffset = offset + 30 + preposition.length
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input ':': expected "," or <EOF> (line 1, column ${finalOffset + 1} (offset: $finalOffset))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Mismatched input ':': expected ';', <EOF> (line 1, column ${finalOffset + 1} (offset: $finalOffset))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DBMS $preposition") {
          val finalOffset = offset + 28 + preposition.length
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input '': expected a parameter or an identifier (line 1, column ${finalOffset + 1} (offset: $finalOffset))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Mismatched input '': expected an identifier, '$$' (line 1, column ${finalOffset + 1} (offset: $finalOffset))"""
            ))
        }

        test(s"$command$immutableString ALL DBMS PRIVILEGES ON DBMS") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input '': expected "$preposition" (line 1, column ${offset + 28} (offset: ${offset + 27}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Mismatched input '': expected '$preposition' (line 1, column ${offset + 28} (offset: ${offset + 27}))"""
            ))
        }

        // Tests for invalid alias management privileges (database keyword in wrong place)

        test(s"$command$immutableString DATABASE ALIAS MANAGEMENT ON DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'ALIAS': expected "MANAGEMENT" (line 1, column ${offset + 10} (offset: ${offset + 9}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Extraneous input 'ALIAS': expected 'MANAGEMENT' (line 1, column ${offset + 10} (offset: ${offset + 9}))"""
            ))
        }

        test(s"$command$immutableString CREATE DATABASE ALIAS ON DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'ALIAS': expected "ON" (line 1, column ${offset + 17} (offset: ${offset + 16}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Extraneous input 'ALIAS': expected 'ON' (line 1, column ${offset + 17} (offset: ${offset + 16}))"""
            ))
        }

        test(s"$command$immutableString DROP DATABASE ALIAS ON DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'ALIAS': expected "ON" (line 1, column ${offset + 15} (offset: ${offset + 14}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Extraneous input 'ALIAS': expected 'ON' (line 1, column ${offset + 15} (offset: ${offset + 14}))"""
            ))
        }

        test(s"$command$immutableString ALTER DATABASE ALIAS ON DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'ALIAS': expected "ON" (line 1, column ${offset + 16} (offset: ${offset + 15}))"""
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Extraneous input 'ALIAS': expected 'ON' (line 1, column ${offset + 16} (offset: ${offset + 15}))"""
            ))
        }

        test(s"$command$immutableString SHOW DATABASE ALIAS ON DBMS $preposition role") {
          testName should notParse[Statements]
            .parseIn(JavaCc)(_.withMessage(
              s"""Invalid input 'DATABASE': expected
                 |  "ALIAS"
                 |  "CONSTRAINT"
                 |  "CONSTRAINTS"
                 |  "INDEX"
                 |  "INDEXES"
                 |  "PRIVILEGE"
                 |  "ROLE"
                 |  "SERVER"
                 |  "SERVERS"
                 |  "SETTING"
                 |  "SETTINGS"
                 |  "TRANSACTION"
                 |  "TRANSACTIONS"
                 |  "USER" (line 1, column ${offset + 6} (offset: ${offset + 5}))""".stripMargin
            ))
            .parseIn(Antlr)(_.throws[SyntaxException].withMessageStart(
              s"""Extraneous input 'DATABASE': expected 'INDEX', 'INDEXES', 'CONSTRAINT', 'CONSTRAINTS', 'TRANSACTION', 'TRANSACTIONS', 'ALIAS', 'PRIVILEGE', 'ROLE', 'SERVER', 'SERVERS', 'SETTING', 'USER' (line 1, column ${offset + 6} (offset: ${offset + 5}))"""
            ))
        }
    }
  }
}
