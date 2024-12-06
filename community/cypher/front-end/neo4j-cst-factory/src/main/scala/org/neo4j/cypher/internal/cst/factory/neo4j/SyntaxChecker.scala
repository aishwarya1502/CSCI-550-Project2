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
package org.neo4j.cypher.internal.cst.factory.neo4j

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode
import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory
import org.neo4j.cypher.internal.ast.factory.ConstraintType
import org.neo4j.cypher.internal.ast.factory.HintIndexType
import org.neo4j.cypher.internal.cst.factory.neo4j.ast.Util.astSeq
import org.neo4j.cypher.internal.cst.factory.neo4j.ast.Util.cast
import org.neo4j.cypher.internal.cst.factory.neo4j.ast.Util.ctxChild
import org.neo4j.cypher.internal.cst.factory.neo4j.ast.Util.nodeChild
import org.neo4j.cypher.internal.cst.factory.neo4j.ast.Util.pos
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.parser.AstRuleCtx
import org.neo4j.cypher.internal.parser.CypherParser
import org.neo4j.cypher.internal.parser.CypherParser.ConstraintExistsContext
import org.neo4j.cypher.internal.parser.CypherParser.ConstraintIsNotNullContext
import org.neo4j.cypher.internal.parser.CypherParser.ConstraintIsUniqueContext
import org.neo4j.cypher.internal.parser.CypherParser.ConstraintKeyContext
import org.neo4j.cypher.internal.parser.CypherParser.ConstraintTypedContext
import org.neo4j.cypher.internal.parser.CypherParser.GlobContext
import org.neo4j.cypher.internal.parser.CypherParser.GlobRecursiveContext
import org.neo4j.cypher.internal.parser.CypherParser.SymbolicAliasNameOrParameterContext
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType
import org.neo4j.cypher.internal.util.symbols.CypherType

import scala.collection.mutable

final class SyntaxChecker(exceptionFactory: CypherExceptionFactory) extends ParseTreeListener {
  private var errors: Seq[Exception] = Seq.empty

  override def visitTerminal(node: TerminalNode): Unit = {}
  override def visitErrorNode(node: ErrorNode): Unit = {}
  override def enterEveryRule(ctx: ParserRuleContext): Unit = {}

  override def exitEveryRule(ctx: ParserRuleContext): Unit = {
    // Note, this has been shown to be significantly faster than using the generated listener.
    // Compiles into a lookupswitch (or possibly tableswitch)
    ctx.getRuleIndex match {
      case CypherParser.RULE_periodicCommitQueryHintFailure   => checkPeriodicCommitQueryHintFailure(cast(ctx))
      case CypherParser.RULE_subqueryInTransactionsParameters => checkSubqueryInTransactionsParameters(cast(ctx))
      case CypherParser.RULE_createCommand                    => checkCreateCommand(cast(ctx))
      case CypherParser.RULE_createConstraint                 => checkCreateConstraint(cast(ctx))
      case CypherParser.RULE_dropConstraint                   => checkDropConstraint(cast(ctx))
      case CypherParser.RULE_createLookupIndex                => checkCreateLookupIndex(cast(ctx))
      case CypherParser.RULE_createUser                       => checkCreateUser(cast(ctx))
      case CypherParser.RULE_alterUser                        => checkAlterUser(cast(ctx))
      case CypherParser.RULE_allPrivilege                     => checkAllPrivilege(cast(ctx))
      case CypherParser.RULE_createDatabase                   => checkCreateDatabase(cast(ctx))
      case CypherParser.RULE_alterDatabase                    => checkAlterDatabase(cast(ctx))
      case CypherParser.RULE_alterDatabaseTopology            => checkAlterDatabaseTopology(cast(ctx))
      case CypherParser.RULE_createAlias                      => checkCreateAlias(cast(ctx))
      case CypherParser.RULE_alterAlias                       => checkAlterAlias(cast(ctx))
      case CypherParser.RULE_globPart                         => checkGlobPart(cast(ctx))
      case CypherParser.RULE_insertPattern                    => checkInsertPattern(cast(ctx))
      case CypherParser.RULE_insertNodeLabelExpression        => checkInsertLabelConjunction(cast(ctx))
      case CypherParser.RULE_functionInvocation               => checkFunctionInvocation(cast(ctx))
      case CypherParser.RULE_typePart                         => checkTypePart(cast(ctx))
      case CypherParser.RULE_hint                             => checkHint(cast(ctx))
      case _                                                  =>
    }
  }

  def check(ctx: ParserRuleContext): Boolean = {
    exitEveryRule(ctx)
    errors.isEmpty
  }

  def getErrors: Seq[Exception] = errors

  def hasErrors: Boolean = errors.nonEmpty

  private def inputPosition(symbol: Token): InputPosition = {
    InputPosition(symbol.getStartIndex, symbol.getLine, symbol.getCharPositionInLine + 1)
  }

  private def errorOnDuplicate(
    token: Token,
    description: String,
    isParam: Boolean = false
  ): Unit = {
    if (isParam) {
      errors :+= exceptionFactory.syntaxException(
        s"Duplicated $description parameters",
        inputPosition(token)
      )
    } else {
      errors :+= exceptionFactory.syntaxException(
        s"Duplicate $description clause",
        inputPosition(token)
      )

    }
  }

  private def errorOnDuplicateTokens(
    params: java.util.List[TerminalNode],
    description: String,
    isParam: Boolean = false
  ): Unit = {
    if (params.size() > 1) {
      errorOnDuplicate(params.get(1).getSymbol, description, isParam)
    }
  }

  private def errorOnDuplicateCtx[T <: AstRuleCtx](
    ctx: java.util.List[T],
    description: String,
    isParam: Boolean = false
  ): Unit = {
    if (ctx.size > 1) {
      errorOnDuplicate(nodeChild(ctx.get(1), 0).getSymbol, description, isParam)
    }
  }

  private def errorOnDuplicateRule[T <: ParserRuleContext](
    params: java.util.List[T],
    description: String,
    isParam: Boolean = false
  ): Unit = {
    if (params.size() > 1) {
      errorOnDuplicate(params.get(1).start, description, isParam)
    }
  }

  private def errorOnAliasNameContainingDots(aliasesNames: java.util.List[SymbolicAliasNameOrParameterContext])
    : Unit = {
    if (aliasesNames.size() > 0) {
      val aliasName = aliasesNames.get(0)
      if (aliasName.symbolicAliasName() != null && aliasName.symbolicAliasName().symbolicNameString().size() > 2) {
        val start = aliasName.symbolicAliasName().symbolicNameString().get(0).getStart
        errors :+= exceptionFactory.syntaxException(
          s"'.' is not a valid character in the remote alias name '${aliasName.getText}'. Remote alias names using '.' must be quoted with backticks e.g. `remote.alias`.",
          inputPosition(start)
        )
      }
    }
  }

  private def checkSubqueryInTransactionsParameters(ctx: CypherParser.SubqueryInTransactionsParametersContext): Unit = {
    errorOnDuplicateRule(ctx.subqueryInTransactionsBatchParameters(), "OF ROWS", isParam = true)
    errorOnDuplicateRule(ctx.subqueryInTransactionsErrorParameters(), "ON ERROR", isParam = true)
    errorOnDuplicateRule(ctx.subqueryInTransactionsReportParameters(), "REPORT STATUS", isParam = true)
  }

  private def checkCreateAlias(ctx: CypherParser.CreateAliasContext): Unit = {
    if (ctx.stringOrParameter() != null)
      errorOnAliasNameContainingDots(ctx.symbolicAliasNameOrParameter())

  }

  private def checkAlterAlias(ctx: CypherParser.AlterAliasContext): Unit = {
    val aliasTargets = ctx.alterAliasTarget()
    val hasUrl = !aliasTargets.isEmpty && aliasTargets.get(0).AT() != null
    val usernames = ctx.alterAliasUser()
    val passwords = ctx.alterAliasPassword()
    val driverSettings = ctx.alterAliasDriver()

    // Should only be checked in case of remote
    if (hasUrl || !usernames.isEmpty || !passwords.isEmpty || !driverSettings.isEmpty)
      errorOnAliasNameContainingDots(java.util.List.of(ctx.symbolicAliasNameOrParameter()))

    errorOnDuplicateCtx(driverSettings, "DRIVER")
    errorOnDuplicateCtx(usernames, "USER")
    errorOnDuplicateCtx(passwords, "PASSWORD")
    errorOnDuplicateCtx(ctx.alterAliasProperties(), "PROPERTIES")
    errorOnDuplicateCtx(aliasTargets, "TARGET")
  }

  private def checkCreateUser(ctx: CypherParser.CreateUserContext): Unit = {
    val changeRequired = ctx.password().passwordChangeRequired()
    if (changeRequired != null && !ctx.PASSWORD().isEmpty) {
      errorOnDuplicate(ctx.PASSWORD().get(0).getSymbol, "SET PASSWORD CHANGE [NOT] REQUIRED")
    } else if (ctx.PASSWORD().size > 1) {
      errorOnDuplicate(ctx.PASSWORD().get(1).getSymbol, "SET PASSWORD CHANGE [NOT] REQUIRED")
    }
    errorOnDuplicateRule(ctx.userStatus(), "SET STATUS {SUSPENDED|ACTIVE}")
    errorOnDuplicateRule(ctx.homeDatabase(), "SET HOME DATABASE")
  }

  private def checkAlterUser(ctx: CypherParser.AlterUserContext): Unit = {
    val pass = ctx.password()
    val passSize = pass.size()
    val nbrSetPass = ctx.PASSWORD().size + pass.size()
    // Set
    if (nbrSetPass > 1) {
      if (ctx.PASSWORD().size > 1) {
        errorOnDuplicateTokens(ctx.PASSWORD(), "SET PASSWORD CHANGE [NOT] REQUIRED")
      } else if (passSize > 0) {
        val hasChange = pass.stream().anyMatch(_.passwordChangeRequired() != null)
        if (ctx.PASSWORD().size > 0 && hasChange) {
          errorOnDuplicate(nodeChild(ctx.password(0), 0).getSymbol, "SET PASSWORD")
        } else if (passSize > 1) {
          errorOnDuplicate(nodeChild(ctx.password(1), 0).getSymbol, "SET PASSWORD")
        }
      }
    }
    errorOnDuplicateRule(ctx.userStatus(), "SET STATUS {SUSPENDED|ACTIVE}")
    errorOnDuplicateRule(ctx.homeDatabase(), "SET HOME DATABASE")
  }

  private def checkAllPrivilege(ctx: CypherParser.AllPrivilegeContext): Unit = {
    val privilegeType = ctx.allPrivilegeType()
    val privilegeTarget = ctx.allPrivilegeTarget()

    if (privilegeType != null) {
      val privilege =
        if (privilegeType.GRAPH() != null) Some("GRAPH")
        else if (privilegeType.DBMS() != null) Some("DBMS")
        else if (privilegeType.DATABASE() != null) Some("DATABASE")
        else None

      val target = privilegeTarget match {
        case c: CypherParser.DefaultTargetContext =>
          privilege match {
            case Some("DBMS") =>
              if (c.HOME() != null) ("HOME", c.HOME().getSymbol)
              else ("DEFAULT", c.DEFAULT().getSymbol)
            case _ =>
              if (c.GRAPH() != null) ("GRAPH", c.GRAPH().getSymbol)
              else ("DATABASE", c.DATABASE().getSymbol)
          }
        case c: CypherParser.DatabaseVariableTargetContext =>
          if (c.DATABASE() != null) ("DATABASE", c.DATABASE().getSymbol)
          else ("DATABASES", c.DATABASES().getSymbol)
        case c: CypherParser.GraphVariableTargetContext =>
          if (c.GRAPH() != null) ("GRAPH", c.GRAPH().getSymbol)
          else ("GRAPHS", c.GRAPHS().getSymbol)
        case c: CypherParser.DBMSTargetContext =>
          ("DBMS", c.DBMS().getSymbol)
        case _ => throw new IllegalStateException("Unexpected privilege all command")
      }
      (privilege, target) match {
        case (Some(privilege), (target, symbol)) =>
          // This makes GRANT ALL DATABASE PRIVILEGES ON DATABASES * work
          if (!target.startsWith(privilege)) {
            errors :+= exceptionFactory.syntaxException(
              s"Invalid input '$target': expected \"$privilege\"",
              inputPosition(symbol)
            )
          }
        case _ =>
      }
    }
  }

  private def checkGlobPart(ctx: CypherParser.GlobPartContext): Unit = {
    if (ctx.DOT() == null) {
      ctx.parent.parent match {
        case r: GlobRecursiveContext if r.globPart().escapedSymbolicNameString() != null =>
          addError()

        case r: GlobContext if r.escapedSymbolicNameString() != null =>
          addError()

        case _ =>
      }

      def addError(): Unit = {
        errors :+= exceptionFactory.syntaxException(
          "Each part of the glob (a block of text up until a dot) must either be fully escaped or not escaped at all.",
          inputPosition(ctx.start)
        )
      }
    }
  }

  private def checkCreateConstraint(ctx: CypherParser.CreateConstraintContext): Unit = {

    ctx.constraintType() match {
      case c: ConstraintIsUniqueContext =>
        if (ctx.commandNodePattern() != null && (c.RELATIONSHIP() != null || c.REL() != null)) {
          errors :+= exceptionFactory.syntaxException(
            s"'${ConstraintType.REL_UNIQUE.description()}' does not allow node patterns",
            inputPosition(ctx.commandNodePattern().getStart)
          )
        }
        if (ctx.commandRelPattern() != null && c.NODE() != null) {
          errors :+= exceptionFactory.syntaxException(
            s"'${ConstraintType.NODE_UNIQUE.description()}' does not allow relationship patterns",
            inputPosition(ctx.commandRelPattern().getStart)
          )
        }
      case c: ConstraintKeyContext =>
        if (ctx.commandNodePattern() != null && (c.RELATIONSHIP() != null || c.REL() != null)) {
          errors :+= exceptionFactory.syntaxException(
            s"'${ConstraintType.REL_KEY.description()}' does not allow node patterns",
            inputPosition(ctx.commandNodePattern().getStart)
          )
        }
        if (ctx.commandRelPattern() != null && c.NODE() != null) {
          errors :+= exceptionFactory.syntaxException(
            s"'${ConstraintType.NODE_KEY.description()}' does not allow relationship patterns",
            inputPosition(ctx.commandRelPattern().getStart)
          )
        }
      case c: ConstraintExistsContext =>
        if (c.propertyList() != null && c.propertyList().property().size() > 1) {
          val secondProperty = c.propertyList().property(1).start
          errors :+= exceptionFactory.syntaxException(
            "Constraint type 'EXISTS' does not allow multiple properties",
            inputPosition(secondProperty)
          )
        }
      case c: ConstraintTypedContext =>
        if (c.propertyList() != null && c.propertyList().property().size() > 1) {
          val secondProperty = c.propertyList().property(1).start
          errors :+= exceptionFactory.syntaxException(
            "Constraint type 'IS TYPED' does not allow multiple properties",
            inputPosition(secondProperty)
          )
        }
      case c: ConstraintIsNotNullContext =>
        if (c.propertyList() != null && c.propertyList().property().size() > 1) {
          val secondProperty = c.propertyList().property(1).start
          errors :+= exceptionFactory.syntaxException(
            "Constraint type 'IS NOT NULL' does not allow multiple properties",
            inputPosition(secondProperty)
          )
        }
      case _ =>
        errors :+= exceptionFactory.syntaxException(
          "Constraint type is not recognized",
          inputPosition(ctx.constraintType().getStart)
        )
    }

  }

  private def checkDropConstraint(ctx: CypherParser.DropConstraintContext): Unit = {
    val relPattern = ctx.commandRelPattern()
    if (relPattern != null) {
      val errorMessageEnd = "does not allow relationship patterns"
      if (ctx.KEY() != null) {
        errors :+= exceptionFactory.syntaxException(
          s"'${ConstraintType.NODE_KEY.description()}' $errorMessageEnd",
          inputPosition(relPattern.getStart)
        )
      } else if (ctx.UNIQUE() != null) {
        errors :+= exceptionFactory.syntaxException(
          s"'${ConstraintType.NODE_UNIQUE.description()}' $errorMessageEnd",
          inputPosition(relPattern.getStart)
        )
      }
    }

    if (ctx.NULL() != null) {
      errors :+= exceptionFactory.syntaxException(
        "Unsupported drop constraint command: Please delete the constraint by name instead",
        inputPosition(ctx.start)
      )
    }
  }

  private def checkCreateDatabase(ctx: CypherParser.CreateDatabaseContext): Unit = {
    errorOnDuplicateRule[CypherParser.PrimaryTopologyContext](ctx.primaryTopology(), "PRIMARY")
    errorOnDuplicateRule[CypherParser.SecondaryTopologyContext](ctx.secondaryTopology(), "SECONDARY")
  }

  private def checkAlterDatabase(ctx: CypherParser.AlterDatabaseContext): Unit = {
    if (!ctx.REMOVE().isEmpty) {
      val keyNames = astSeq[String](ctx.symbolicNameString())
      val keySet = mutable.Set.empty[String]
      var i = 0
      keyNames.foreach(k =>
        if (keySet.contains(k)) {
          errors :+= exceptionFactory.syntaxException(
            s"Duplicate 'REMOVE OPTION $k' clause",
            pos(ctx.symbolicNameString(i))
          )
        } else {
          keySet.addOne(k)
          i += 1
        }
      )
    }

    if (!ctx.alterDatabaseOption().isEmpty) {
      val optionCtxs = astSeq[Map[String, Expression]](ctx.alterDatabaseOption())
      // TODO odd why can m be null, shouldn't it fail before this.
      val keyNames = optionCtxs.flatMap(m => if (m != null) m.keys else Seq.empty)
      val keySet = mutable.Set.empty[String]
      var i = 0
      keyNames.foreach(k =>
        if (keySet.contains(k)) {
          errors :+= exceptionFactory.syntaxException(
            s"Duplicate 'SET OPTION $k' clause",
            pos(ctx.alterDatabaseOption(i))
          )
        } else {
          keySet.addOne(k)
          i += 1
        }
      )
    }

    errorOnDuplicateCtx(ctx.alterDatabaseAccess(), "ACCESS")

    val topology = ctx.alterDatabaseTopology()
    errorOnDuplicateCtx(topology, "TOPOLOGY")
  }

  private def checkAlterDatabaseTopology(ctx: CypherParser.AlterDatabaseTopologyContext): Unit = {
    errorOnDuplicateRule[CypherParser.PrimaryTopologyContext](ctx.primaryTopology(), "PRIMARY")
    errorOnDuplicateRule[CypherParser.SecondaryTopologyContext](ctx.secondaryTopology(), "SECONDARY")
  }

  private def checkPeriodicCommitQueryHintFailure(ctx: CypherParser.PeriodicCommitQueryHintFailureContext): Unit = {
    val periodic = ctx.PERIODIC().getSymbol

    errors :+= exceptionFactory.syntaxException(
      "The PERIODIC COMMIT query hint is no longer supported. Please use CALL { ... } IN TRANSACTIONS instead.",
      inputPosition(periodic)
    )
  }

  private def checkCreateCommand(ctx: CypherParser.CreateCommandContext): Unit = {
    val createIndex = ctx.createIndex()
    val replace = ctx.REPLACE()

    if (createIndex != null && replace != null) {
      if (createIndex.oldCreateIndex() != null) {
        errors :+= exceptionFactory.syntaxException(
          "'REPLACE' is not allowed for this index syntax",
          inputPosition(replace.getSymbol)
        )
      }
    }
  }

  private def checkCreateLookupIndex(ctx: CypherParser.CreateLookupIndexContext): Unit = {
    val functionName = ctx.symbolicNameString()
    /* This should not be valid:
         CREATE LOOKUP INDEX FOR ()-[x]-() ON EACH(x)

         This should be valid:
         CREATE LOOKUP INDEX FOR ()-[x]-() ON EACH EACH(x)
     */
    val relPattern = ctx.lookupIndexRelPattern()
    if (functionName.getText.toUpperCase() == "EACH" && relPattern != null && relPattern.EACH() == null) {

      errors :+= exceptionFactory.syntaxException(
        "Missing function name for the LOOKUP INDEX",
        inputPosition(ctx.LPAREN().getSymbol)
      )
    }
  }

  private def checkInsertPattern(ctx: CypherParser.InsertPatternContext): Unit = {
    if (ctx.EQ() != null) {
      errors :+= exceptionFactory.syntaxException(
        "Named patterns are not allowed in `INSERT`. Use `CREATE` instead or remove the name.",
        pos(ctxChild(ctx, 0))
      )
    }
  }

  private def checkInsertLabelConjunction(ctx: CypherParser.InsertNodeLabelExpressionContext): Unit = {
    val colons = ctx.COLON()
    val firstIsColon = nodeChild(ctx, 0).getSymbol.getType == CypherParser.COLON

    if (firstIsColon && colons.size > 1) {
      errors :+= exceptionFactory.syntaxException(
        "Colon `:` conjunction is not allowed in INSERT. Use `CREATE` or conjunction with ampersand `&` instead.",
        inputPosition(colons.get(1).getSymbol)
      )
    } else if (!firstIsColon && colons.size() > 0) {
      errors :+= exceptionFactory.syntaxException(
        "Colon `:` conjunction is not allowed in INSERT. Use `CREATE` or conjunction with ampersand `&` instead.",
        inputPosition(colons.get(0).getSymbol)
      )
    }
  }

  private def checkFunctionInvocation(ctx: CypherParser.FunctionInvocationContext): Unit = {
    val functionName = ctx.functionName().symbolicNameString().ast[String]()
    functionName match {
      case "normalize" =>
        if (ctx.functionArgument().size == 2) {
          errors :+= exceptionFactory.syntaxException(
            "Invalid normal form, expected NFC, NFD, NFKC, NFKD",
            ctx.functionArgument(1).expression().ast[Expression]().position
          )
        }
      case _ =>
    }
  }

  private def checkTypePart(ctx: CypherParser.TypePartContext): Unit = {
    val cypherType = ctx.typeName().ast[CypherType]()
    if (cypherType.isInstanceOf[ClosedDynamicUnionType] && ctx.typeNullability() != null) {
      errors :+= exceptionFactory.syntaxException(
        "Closed Dynamic Union Types can not be appended with `NOT NULL`, specify `NOT NULL` on all inner types instead.",
        pos(ctx.typeNullability())
      )
    }
  }

  private def checkHint(ctx: CypherParser.HintContext): Unit = {
    nodeChild(ctx, 1).getSymbol.getType match {
      case CypherParser.BTREE => errors :+= exceptionFactory.syntaxException(
          ASTExceptionFactory.invalidHintIndexType(HintIndexType.BTREE),
          pos(nodeChild(ctx, 1))
        )
      case _ =>
    }
  }
}
