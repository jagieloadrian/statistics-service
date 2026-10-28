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

        test("rejects unknown or non-numeric names and anything SQL-looking") {
            listOf("a + x", "lockdown * 2", "a; DROP TABLE records", "a + 'b'", "a -- b", "a + \"b\"", "1.2.3", "a +", "(a", "a b")
                .forEach { withClue(it) { shouldThrow<IllegalArgumentException> { Expr.toSql(it, names) } } }
        }
    })
