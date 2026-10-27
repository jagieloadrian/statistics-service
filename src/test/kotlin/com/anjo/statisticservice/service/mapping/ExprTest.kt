package com.anjo.statisticservice.service.mapping

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExprTest {
    private val names = setOf("a", "b", "c")
    private fun sql(e: String) = Expr.toSql(e, names)
    private fun v(n: String) = "(vals->>'$n')::float8"

    @Test
    fun `precedence and parentheses`() {
        sql("a + b * c") shouldBe "(${v("a")} + (${v("b")} * ${v("c")}))"
        sql("(a + b) * c") shouldBe "((${v("a")} + ${v("b")}) * ${v("c")})"
        sql("a - b - c") shouldBe "((${v("a")} - ${v("b")}) - ${v("c")})"
    }

    @Test
    fun `numbers and division with NULLIF`() {
        sql("a / 2.5") shouldBe "(${v("a")} / NULLIF(2.5::float8, 0))"
        sql("(a + b) / c") shouldBe "((${v("a")} + ${v("b")}) / NULLIF(${v("c")}, 0))"
    }

    @Test
    fun `rejects unknown or non-numeric names and anything SQL-looking`() {
        listOf("a + x", "lockdown * 2", "a; DROP TABLE records", "a + 'b'", "a -- b", "a + \"b\"", "1.2.3", "a +", "(a", "a b")
            .forEach { assertThrows<IllegalArgumentException>(it) { Expr.toSql(it, names) } }
    }
}
