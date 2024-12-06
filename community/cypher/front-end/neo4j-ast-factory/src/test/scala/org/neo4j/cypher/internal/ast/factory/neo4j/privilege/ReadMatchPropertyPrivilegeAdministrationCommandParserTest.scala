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

import org.neo4j.cypher.internal.ast.ActionResource
import org.neo4j.cypher.internal.ast.AllGraphsScope
import org.neo4j.cypher.internal.ast.AllPropertyResource
import org.neo4j.cypher.internal.ast.DefaultGraphScope
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.GraphAction
import org.neo4j.cypher.internal.ast.GraphPrivilege
import org.neo4j.cypher.internal.ast.HomeGraphScope
import org.neo4j.cypher.internal.ast.LabelAllQualifier
import org.neo4j.cypher.internal.ast.LabelQualifier
import org.neo4j.cypher.internal.ast.Match
import org.neo4j.cypher.internal.ast.MatchAction
import org.neo4j.cypher.internal.ast.NamedGraphsScope
import org.neo4j.cypher.internal.ast.PatternQualifier
import org.neo4j.cypher.internal.ast.PropertiesResource
import org.neo4j.cypher.internal.ast.ReadAction
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.expressions.BooleanExpression
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.MapExpression
import org.neo4j.cypher.internal.expressions.MatchMode
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.Pattern.ForMatch
import org.neo4j.cypher.internal.expressions.PatternPart.AllPaths
import org.neo4j.cypher.internal.expressions.PatternPartWithSelector
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.Variable

import scala.util.Random

class ReadMatchPropertyPrivilegeAdministrationCommandParserTest
    extends PropertyPrivilegeAdministrationCommandParserTestBase {
  case class Action(action: GraphAction, verb: String, preposition: String, func: resourcePrivilegeFunc)

  case class Resource(properties: String, resource: ActionResource)

  val actions: Seq[Action] = Seq(
    Action(ReadAction, "GRANT", "TO", grantGraphPrivilege: resourcePrivilegeFunc),
    Action(ReadAction, "DENY", "TO", denyGraphPrivilege: resourcePrivilegeFunc),
    Action(ReadAction, "REVOKE GRANT", "FROM", revokeGrantGraphPrivilege: resourcePrivilegeFunc),
    Action(ReadAction, "REVOKE DENY", "FROM", revokeDenyGraphPrivilege: resourcePrivilegeFunc),
    Action(ReadAction, "REVOKE", "FROM", revokeGraphPrivilege: resourcePrivilegeFunc),
    Action(MatchAction, "GRANT", "TO", grantGraphPrivilege: resourcePrivilegeFunc),
    Action(MatchAction, "DENY", "TO", denyGraphPrivilege: resourcePrivilegeFunc),
    Action(MatchAction, "REVOKE GRANT", "FROM", revokeGrantGraphPrivilege: resourcePrivilegeFunc),
    Action(MatchAction, "REVOKE DENY", "FROM", revokeDenyGraphPrivilege: resourcePrivilegeFunc),
    Action(MatchAction, "REVOKE", "FROM", revokeGraphPrivilege: resourcePrivilegeFunc)
  )

  val resources: Seq[Resource] = Seq(
    Resource("*", AllPropertyResource()(pos)),
    Resource("bar", PropertiesResource(Seq("bar"))(pos)),
    Resource("foo, bar", PropertiesResource(Seq("foo", "bar"))(pos))
  )

  test("HOME GRAPH") {
    for {
      Action(action, verb, preposition, func) <- actions
      immutable <- Seq(true, false)
    } yield {
      val immutableString = immutableOrEmpty(immutable)

      parsing(
        s"$verb$immutableString ${action.name} { prop } ON HOME GRAPH FOR (a:A) WHERE a.prop2=1 $preposition role"
      ) shouldGive func(
        GraphPrivilege(action, HomeGraphScope()(pos))(pos),
        PropertiesResource(propSeq)(pos),
        List(PatternQualifier(
          Seq(labelQualifierA),
          Some(Variable("a")(_)),
          Equals(
            Property(Variable("a")(_), PropertyKeyName("prop2")(_))(_),
            literal(1)
          )(_)
        )),
        Seq(literalRole),
        immutable
      )
    }
  }

  test("DEFAULT GRAPH") {
    for {
      Action(action, verb, preposition, func) <- actions
      immutable <- Seq(true, false)
    } yield {
      val immutableString = immutableOrEmpty(immutable)

      parsing(
        s"$verb$immutableString ${action.name} { prop } ON DEFAULT GRAPH FOR (a:A) WHERE a.prop2=1 $preposition role"
      ) shouldGive func(
        GraphPrivilege(action, DefaultGraphScope()(pos))(pos),
        PropertiesResource(propSeq)(pos),
        List(PatternQualifier(
          Seq(labelQualifierA),
          Some(Variable("a")(_)),
          Equals(
            Property(Variable("a")(_), PropertyKeyName("prop2")(_))(_),
            literal(1)
          )(_)
        )),
        Seq(literalRole),
        immutable
      )
    }
  }

  test("valid labels") {
    for {
      Action(action, verb, preposition, func) <- actions
      immutable <- Seq(true, false)
      graphKeyword <- graphKeywords
      LiteralExpression(expression, propertyRuleAst) <- literalExpressions
      Resource(properties, resource) <- resources
      Scope(graphName, graphScope) <- scopes
    } yield {
      val immutableString = immutableOrEmpty(immutable)
      val expressionString = expressionStringifier(expression)

      // No labels
      (expression match {
        case _: MapExpression => List(
            (None, s"($expressionString)"),
            (Some(Variable("n")(pos)), s"(n $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n WHERE $expressionString)"),
            (Some(Variable("WHERE")(pos)), s"(WHERE WHERE $expressionString)"), // WHERE as variable
            (
              None,
              s"() WHERE $expressionString"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        // All labels, parameterised role
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition $$role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(PatternQualifier(Seq(LabelAllQualifier()(pos)), variable, propertyRuleAst)),
            Seq(paramRole),
            immutable
          )

        // All labels, role containing colon
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition `r:ole`"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(PatternQualifier(Seq(LabelAllQualifier()(pos)), variable, propertyRuleAst)),
            Seq(literalRColonOle),
            immutable
          )
      }

      // Single label name
      (expression match {
        case _: MapExpression => List(
            (None, s"(:A $expressionString)"),
            (Some(Variable("n")(pos)), s"(n:A $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n:A) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n:A WHERE $expressionString)"),
            (Some(Variable("WHERE")(pos)), s"(WHERE:A WHERE $expressionString)"), // WHERE as variable
            (
              None,
              s"(:A) WHERE $expressionString"
            ), // Missing variable is valid when parsing. Fail in semantic check
            (
              None,
              s"(:A WHERE $expressionString)"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(PatternQualifier(Seq(labelQualifierA), variable, propertyRuleAst)),
            Seq(literalRole),
            immutable
          )
      }

      // Escaped multi-token label name
      (expression match {
        case _: MapExpression => List(
            (None, s"(:`A B` $expressionString)"),
            (Some(Variable("n")(pos)), s"(n:`A B` $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n:`A B`) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n:`A B` WHERE $expressionString)"),
            (
              None,
              s"(:`A B`) WHERE $expressionString"
            ), // Missing variable is valid when parsing. Fail in semantic check
            (
              None,
              s"(:`A B` WHERE $expressionString)"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(PatternQualifier(Seq(LabelQualifier("A B")(_)), variable, propertyRuleAst)),
            Seq(literalRole),
            immutable
          )
      }

      // Label containing colon
      (expression match {
        case _: MapExpression => List(
            (None, s"(:`:A` $expressionString)"),
            (Some(Variable("n")(pos)), s"(n:`:A` $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n:`:A`) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n:`:A` WHERE $expressionString)"),
            (
              None,
              s"(:`:A`) WHERE $expressionString"
            ), // Missing variable is valid when parsing. Fail in semantic check
            (
              None,
              s"(:`:A` WHERE $expressionString)"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(PatternQualifier(Seq(LabelQualifier(":A")(_)), variable, propertyRuleAst)),
            Seq(literalRole),
            immutable
          )
      }

      // Multiple labels
      (expression match {
        case _: MapExpression => List(
            (None, s"(:A|B $expressionString)"),
            (Some(Variable("n")(pos)), s"(n:A|B $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n:A|B) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n:A|B WHERE $expressionString)"),
            (
              None,
              s"(:A|B) WHERE $expressionString"
            ), // Missing variable is valid when parsing. Fail in semantic check
            (
              None,
              s"(:A|B WHERE $expressionString)"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        parsing(
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition role1, $$role2"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScope)(pos),
            resource,
            List(
              PatternQualifier(Seq(labelQualifierA, labelQualifierB), variable, propertyRuleAst)
            ),
            Seq(literalRole1, paramRole2),
            immutable
          )
      }
    }
  }

  test("additional assortment of supported graph scopes and property resources") {
    for {
      Action(action, verb, preposition, func) <- actions
      immutable <- Seq(true, false)
      graphKeyword <- graphKeywords
      LiteralExpression(expression, propertyRuleAst) <- literalExpressions
    } yield {
      val immutableString = immutableOrEmpty(immutable)
      val expressionString = expressionStringifier(expression)

      (expression match {
        case _: MapExpression => List(
            (None, s"(:A $expressionString)"),
            (Some(Variable("n")(pos)), s"(n:A $expressionString)")
          )
        case _: BooleanExpression => List(
            (Some(Variable("n")(pos)), s"(n:A) WHERE $expressionString"),
            (Some(Variable("n")(pos)), s"(n:A WHERE $expressionString)"),
            (
              None,
              s"(:A) WHERE $expressionString"
            ), // Missing variable is valid when parsing. Fail in semantic check
            (
              None,
              s"(:A WHERE $expressionString)"
            ) // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { case (variable: Option[Variable], propertyRule: String) =>
        val patternQualifier = List(PatternQualifier(Seq(labelQualifierA), variable, propertyRuleAst))
        parsing(
          s"$verb$immutableString ${action.name} {*} ON $graphKeyword `f:oo` $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, NamedGraphsScope(Seq(namespacedName("f:oo"))) _)(pos),
            AllPropertyResource() _,
            patternQualifier,
            Seq(literalRole),
            immutable
          )
        parsing(
          s"$verb$immutableString ${action.name} {bar} ON $graphKeyword `f:oo` $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, NamedGraphsScope(Seq(namespacedName("f:oo"))) _)(pos),
            PropertiesResource(Seq("bar")) _,
            patternQualifier,
            Seq(literalRole),
            immutable
          )
        parsing(
          s"$verb$immutableString ${action.name} {`b:ar`} ON $graphKeyword foo $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScopeFoo)(pos),
            PropertiesResource(Seq("b:ar")) _,
            patternQualifier,
            Seq(literalRole),
            immutable
          )
        parsing(
          s"$verb$immutableString ${action.name} {*} ON $graphKeyword foo, baz $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScopeFooBaz)(pos),
            AllPropertyResource() _,
            patternQualifier,
            Seq(literalRole),
            immutable
          )
        parsing(
          s"$verb$immutableString ${action.name} {bar} ON $graphKeyword foo, baz $patternKeyword $propertyRule $preposition role"
        ) shouldGive
          func(
            GraphPrivilege(action, graphScopeFooBaz)(pos),
            PropertiesResource(Seq("bar")) _,
            patternQualifier,
            Seq(literalRole),
            immutable
          )
      }
    }
  }

  test("Allow trailing star") {
    parsing(
      s"GRANT READ {*} ON GRAPH * FOR (n) WHERE n.prop1 = 1 (*) TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(ReadAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("n")(_)),
          equals(prop(varFor("n"), "prop1"), literalInt(1))
        )),
        Seq(literalRole),
        i = false
      )

    parsing(
      s"GRANT MATCH {*} ON GRAPH * FOR (n) WHERE n.prop1 = 1 (*) TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(MatchAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("n")(_)),
          equals(prop(varFor("n"), "prop1"), literalInt(1))
        )),
        Seq(literalRole),
        i = false
      )
  }

  test(
    "Different variable should parse correctly to allow them to be rejected in the semantic check with a user-friendly explanation"
  ) {
    parsing(
      s"GRANT READ {*} ON GRAPH * FOR (a) WHERE b.prop1 = 1 TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(ReadAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("a")(_)),
          equals(prop(varFor("b"), "prop1"), literalInt(1))
        )),
        Seq(literalRole),
        i = false
      )

    parsing(
      s"GRANT MATCH {*} ON GRAPH * FOR (a) WHERE b.prop1 = 1 TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(MatchAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("a")(_)),
          equals(prop(varFor("b"), "prop1"), literalInt(1))
        )),
        Seq(literalRole),
        i = false
      )
  }

  test(
    "'FOR (n) WHERE 1 = n.prop1 (foo) TO role' parse as a function to then be rejected in semantic check"
  ) {
    parsing(
      s"GRANT READ {*} ON GRAPH * FOR (n) WHERE 1 = n.prop1 (foo) TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(ReadAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("n")(_)),
          equals(
            literalInt(1),
            FunctionInvocation.apply(
              FunctionName(Namespace(List("n"))(pos), "prop1")(pos),
              Variable("foo")(pos)
            )(pos)
          )
        )),
        Seq(literalRole),
        i = false
      )

    parsing(
      s"GRANT MATCH {*} ON GRAPH * FOR (n WHERE 1 = n.prop1 (foo)) TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(MatchAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelAllQualifier() _),
          Some(Variable("n")(_)),
          equals(
            literalInt(1),
            FunctionInvocation.apply(
              FunctionName(Namespace(List("n"))(pos), "prop1")(pos),
              Variable("foo")(pos)
            )(pos)
          )
        )),
        Seq(literalRole),
        i = false
      )
  }

  test(
    "'(n:A WHERE EXISTS { MATCH (n) })' parse to then be rejected in semantic check"
  ) {
    parsing(
      s"GRANT READ {*} ON GRAPH * FOR (n:A WHERE EXISTS { MATCH (n) }) TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(ReadAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelQualifier("A") _),
          Some(Variable("n")(_)),
          ExistsExpression(
            SingleQuery(
              List(
                Match(
                  optional = false,
                  MatchMode.DifferentRelationships(implicitlyCreated = true)(pos),
                  ForMatch(List(PatternPartWithSelector(
                    AllPaths()(pos),
                    PathPatternPart(NodePattern(Some(Variable("n")(pos)), None, None, None)(pos))
                  )))(pos),
                  List(),
                  None
                )(pos)
              )
            )(pos)
          )(pos, None, None)
        )),
        Seq(literalRole),
        i = false
      )

    parsing(
      s"GRANT MATCH {*} ON GRAPH * FOR (n:A) WHERE EXISTS { MATCH (n) } TO role"
    ) shouldGive
      grantGraphPrivilege(
        GraphPrivilege(MatchAction, AllGraphsScope()(pos))(pos),
        AllPropertyResource() _,
        List(PatternQualifier(
          Seq(LabelQualifier("A") _),
          Some(Variable("n")(_)),
          ExistsExpression(
            SingleQuery(
              List(
                Match(
                  optional = false,
                  MatchMode.DifferentRelationships(implicitlyCreated = true)(pos),
                  ForMatch(List(PatternPartWithSelector(
                    AllPaths()(pos),
                    PathPatternPart(NodePattern(Some(Variable("n")(pos)), None, None, None)(pos))
                  )))(pos),
                  List(),
                  None
                )(pos)
              )
            )(pos)
          )(pos, None, None)
        )),
        Seq(literalRole),
        i = false
      )
  }

  test("legitimate property rules, but with problems elsewhere in the privilege command") {
    // To limit the amount of tests run (and reduce test setup time)
    // only add 25 union combinations, but make it a random mix of combinations
    val randomSamplesOfLiteralExpressions = Random.shuffle(literalExpressions).take(50)

    for {
      Action(action, verb, preposition, _) <- actions
      immutable <- Seq(true, false)
      graphKeyword <- graphKeywords
      LiteralExpression(expression, _) <- randomSamplesOfLiteralExpressions
      Resource(properties, _) <- resources
      Scope(graphName, _) <- scopes
    } yield {
      val immutableString = immutableOrEmpty(immutable)
      val expressionString = expressionStringifier(expression)

      (expression match {
        case _: MapExpression => List(
            s"($expressionString)",
            s"(:A $expressionString)",
            s"(n:A $expressionString)"
          )
        case _: BooleanExpression => List(
            s"(n) WHERE $expressionString",
            s"(n WHERE $expressionString)",
            s"(n:A) WHERE $expressionString",
            s"(n:A WHERE $expressionString)",
            s"(:A) WHERE $expressionString", // Missing variable is valid when parsing. Fail in semantic check
            s"() WHERE $expressionString", // Missing variable is valid when parsing. Fail in semantic check
            s"(:A WHERE $expressionString)" // Missing variable is valid when parsing. Fail in semantic check
          )
        case _ => fail("Unexpected expression")
      }).foreach { (propertyRule: String) =>
        // Missing ON
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} $graphKeyword $graphName $patternKeyword $propertyRule $preposition role"
        )

        // Missing role
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule"
        )

        // r:ole is invalid
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $propertyRule $preposition r:ole"
        )

        // Invalid graph name
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword f:oo $patternKeyword $propertyRule $preposition role"
        )

        // Mixing specific graph and *
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword foo, * $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword *, foo $patternKeyword $propertyRule $preposition role"
        )

        // Invalid property definition
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {b:ar} ON $graphKeyword foo $patternKeyword $propertyRule $preposition role"
        )

        // Missing graph name
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $patternKeyword $propertyRule (*) $preposition role"
        )

        // Missing property definition
        assertFails[Statements](
          s"$verb$immutableString ${action.name} ON $graphKeyword * $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} ON $graphKeyword * $patternKeyword $propertyRule (*) $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} ON $graphKeyword foo $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} ON $graphKeyword foo $patternKeyword $propertyRule (*) $preposition role"
        )

        // Missing property list
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {} ON $graphKeyword * $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {} ON $graphKeyword * $patternKeyword $propertyRule (*) $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {} ON $graphKeyword foo $patternKeyword $propertyRule $preposition role"
        )
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {} ON $graphKeyword foo $patternKeyword $propertyRule (*) $preposition role"
        )
      }
    }
  }

  test("invalid segments") {
    for {
      Action(action, verb, preposition, _) <- actions
      immutable <- Seq(true, false)
      graphKeyword <- graphKeywords
      segment <- invalidSegments
      Resource(properties, _) <- resources
      Scope(graphName, _) <- scopes
    } yield {
      val immutableString = immutableOrEmpty(immutable)

      Seq(
        s"(n:A) WHERE n.prop1 = 1",
        s"(n:A WHERE n.prop1 = 1)",
        s"(:A {prop1:1})",
        s"(n:A {prop1:1})"
      ).foreach { (propertyRule: String) =>
        {
          assertFails[Statements](
            s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $segment $propertyRule $preposition role"
          )
        }
      }
    }
  }

  test("disallowed property rules") {
    for {
      Action(action, verb, preposition, _) <- actions
      immutable <- Seq(true, false)
      graphKeyword <- graphKeywords
      Resource(properties, _) <- resources
      Scope(graphName, _) <- scopes
    } yield {
      val immutableString = immutableOrEmpty(immutable)

      disallowedPropertyRules.foreach { (disallowedPropertyRule: String) =>
        assertFails[Statements](
          s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword $disallowedPropertyRule $preposition role"
        )
      }

      // No variable, WHERE gets parsed as variable in javacc
      assertFailsOnlyJavaCC[Statements](
        s"$verb$immutableString ${action.name} {$properties} ON $graphKeyword $graphName $patternKeyword (WHERE n.prop1 = 1) $preposition role"
      )
    }
  }
}
