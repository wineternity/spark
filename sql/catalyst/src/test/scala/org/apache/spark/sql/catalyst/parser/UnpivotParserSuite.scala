/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.parser

import org.apache.spark.sql.catalyst.analysis._
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, Unpivot}

class UnpivotParserSuite extends AnalysisTest {

  import CatalystSqlParser._
  import org.apache.spark.sql.catalyst.dsl.expressions._
  import org.apache.spark.sql.catalyst.dsl.plans._

  private def assertEqual(sqlCommand: String, plan: LogicalPlan): Unit = {
    comparePlans(parsePlan(sqlCommand), plan, checkAnalysis = false)
  }

  private def intercept(sqlCommand: String, errorClass: Option[String], messages: String*): Unit =
    interceptParseException(parsePlan)(sqlCommand, messages: _*)(errorClass)

  test("unpivot - single value") {
    assertEqual(
      "SELECT * FROM t UNPIVOT (val FOR col in (a, b))",
      Unpivot(
        None,
        Some(Seq(Seq(UnresolvedAlias($"a")), Seq(UnresolvedAlias($"b")))),
        None,
        "col",
        Seq("val"),
        table("t"))
        .where(coalesce($"val").isNotNull)
        .select(star())
    )
  }

  test("unpivot - single value with alias") {
    Seq(
      "SELECT * FROM t UNPIVOT (val FOR col in (a A, b))",
      "SELECT * FROM t UNPIVOT (val FOR col in (a AS A, b))"
    ).foreach { sql =>
      withClue(sql) {
        assertEqual(
          sql,
          Unpivot(
            None,
            Some(Seq(Seq(UnresolvedAlias($"a")), Seq(UnresolvedAlias($"b")))),
            Some(Seq(Some("A"), None)),
            "col",
            Seq("val"),
            table("t"))
            .where(coalesce($"val").isNotNull)
            .select(star())
        )
      }
    }
  }

  test("unpivot - multiple values") {
    assertEqual(
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ((a, b), (c, d)))",
      Unpivot(
        None,
        Some(Seq(Seq($"a", $"b").map(UnresolvedAlias(_)), Seq($"c", $"d").map(UnresolvedAlias(_)))),
        None,
        "col",
        Seq("val1", "val2"),
        table("t"))
        .where(coalesce($"val1", $"val2").isNotNull)
        .select(star())
    )
  }

  test("unpivot - multiple values with alias") {
    Seq(
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ((a, b) first, (c, d)))",
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ((a, b) AS first, (c, d)))"
    ).foreach { sql =>
      withClue(sql) {
        assertEqual(
          sql,
          Unpivot(
            None,
            Some(Seq(
              Seq($"a", $"b").map(UnresolvedAlias(_)),
              Seq($"c", $"d").map(UnresolvedAlias(_))
            )),
            Some(Seq(Some("first"), None)),
            "col",
            Seq("val1", "val2"),
            table("t"))
            .where(coalesce($"val1", $"val2").isNotNull)
            .select(star())
        )
      }
    }
  }

  test("unpivot - multiple values with inner alias") {
    Seq(
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ((a A, b), (c, d)))",
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ((a AS A, b), (c, d)))"
    ).foreach { sql =>
      withClue(sql) {
        intercept(sql, Some("PARSE_SYNTAX_ERROR"), "Syntax error at or near ")
      }
    }
  }

  test("unpivot - alias") {
    Seq(
      "SELECT up.* FROM t UNPIVOT (val FOR col in (a, b)) up",
      "SELECT up.* FROM t UNPIVOT (val FOR col in (a, b)) AS up"
    ).foreach { sql =>
      withClue(sql) {
        assertEqual(
          sql,
          Unpivot(
            None,
            Some(Seq(Seq(UnresolvedAlias($"a")), Seq(UnresolvedAlias($"b")))),
            None,
            "col",
            Seq("val"),
            table("t"))
            .where(coalesce($"val").isNotNull)
            .subquery("up")
            .select(star("up"))
        )
      }
    }
  }

  test("unpivot - no unpivot value names") {
    intercept(
      "SELECT * FROM t UNPIVOT (() FOR col in ((a, b), (c, d)))",
      Some("PARSE_SYNTAX_ERROR"), "Syntax error at or near "
    )
  }

  test("unpivot - no unpivot columns") {
    Seq(
      "SELECT * FROM t UNPIVOT (val FOR col in ())",
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in ())",
      "SELECT * FROM t UNPIVOT ((val1, val2) FOR col in (()))"
    ).foreach { sql =>
      withClue(sql) {
        intercept(sql, Some("PARSE_SYNTAX_ERROR"), "Syntax error at or near ")
      }
    }
  }

  test("unpivot - exclude nulls") {
    assertEqual(
      "SELECT * FROM t UNPIVOT EXCLUDE NULLS (val FOR col in (a, b))",
      Unpivot(
        None,
        Some(Seq(Seq(UnresolvedAlias($"a")), Seq(UnresolvedAlias($"b")))),
        None,
        "col",
        Seq("val"),
        table("t"))
        .where(coalesce($"val").isNotNull)
        .select(star())
    )
  }

  test("unpivot - include nulls") {
    assertEqual(
      "SELECT * FROM t UNPIVOT INCLUDE NULLS (val FOR col in (a, b))",
      Unpivot(
        None,
        Some(Seq(Seq(UnresolvedAlias($"a")), Seq(UnresolvedAlias($"b")))),
        None,
        "col",
        Seq("val"),
        table("t"))
        .select(star())
    )
  }

}
