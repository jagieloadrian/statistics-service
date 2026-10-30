package com.anjo.statisticservice.service.mapping

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExprTest :
    FunSpec({
        val names = setOf("a", "b", "c")

        fun sql(e: String) = Expr.toSql(e, names)

        fun v(n: String) = "(vals->>'$n')::float8"

        test("precedence and parentheses") {
            sql("a + b * c") shouldBe "(${v("a")} + (${v("b")} * ${v("c")}))"
            sql("(a + b) * c") shouldBe "((${v("a")} + ${v("b")}) * ${v("c")})"
            sql("a - b - c") shouldBe "((${v("a")} - ${v("b")}) - ${v("c")})"
        }

        test("numbers and division with NULLIF") {
            sql("a / 2.5") shouldBe "(${v("a")} / NULLIF(2.5::float8, 0))"
            sql("(a + b) / c") shouldBe "((${v("a")} + ${v("b")}) / NULLIF(${v("c")}, 0))"
        }

        test("underscored names, whitespace and nested parentheses") {
            Expr.toSql(" ( _x1 + 2 ) * ( a / ( b - 1 ) ) ", setOf("_x1", "a", "b")) shouldBe
                "(((vals->>'_x1')::float8 + 2::float8) * (${v("a")} / NULLIF((${v("b")} - 1::float8), 0)))"
        }

        test("each kind of mistake names itself") {
            mapOf(
                "a b" to "unexpected 'b' in 'a b'",
                "a + 'b'" to "unexpected ''' in 'a + 'b''",
                "a +" to "unexpected end in 'a +'",
                "(a b" to "missing ')' in '(a b'",
                "1.2.3" to "bad number '1.2.3' in '1.2.3'",
                "a * x" to "'x' is not a number measure in 'a * x'",
            ).forEach { (expr, message) ->
                withClue(expr) { shouldThrow<IllegalArgumentException> { Expr.toSql(expr, names) }.message shouldBe message }
            }
        }

        test("rejects unknown or non-numeric names and anything SQL-looking") {
            listOf("a + x", "lockdown * 2", "a; DROP TABLE records", "a + 'b'", "a -- b", "a + \"b\"", "1.2.3", "a +", "(a", "a b")
                .forEach { withClue(it) { shouldThrow<IllegalArgumentException> { Expr.toSql(it, names) } } }
        }
    })
