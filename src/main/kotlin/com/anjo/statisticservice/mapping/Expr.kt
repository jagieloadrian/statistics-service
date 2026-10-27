package com.anjo.statisticservice.mapping

/**
 * Calculated measure → SQL over one record: numbers, number measures,
 * `+ - * /` and parentheses only. Every other character is rejected, so the output
 * can be inlined. Division by zero gives null.
 */
object Expr {
    fun toSql(expr: String, numberMeasures: Set<String>): String = Parser(expr, numberMeasures).parse()

    private class Parser(val s: String, val names: Set<String>) {
        var i = 0

        fun parse(): String = sum().also { skip(); if (i < s.length) fail("unexpected '${s[i]}'") }

        fun sum(): String {
            var left = product()
            while (true) {
                val op = peek()?.takeIf { it == '+' || it == '-' } ?: return left
                i++
                left = "($left $op ${product()})"
            }
        }

        fun product(): String {
            var left = atom()
            while (true) {
                val op = peek()?.takeIf { it == '*' || it == '/' } ?: return left
                i++
                val right = atom()
                left = if (op == '/') "($left / NULLIF($right, 0))" else "($left * $right)"
            }
        }

        // ponytail: no unary minus; write `0 - x`
        fun atom(): String {
            val c = peek() ?: fail("unexpected end")
            return when {
                c == '(' -> {
                    i++
                    sum().also { if (peek() != ')') fail("missing ')'"); i++ }
                }
                c.isDigit() -> {
                    val text = take { it.isDigit() || it == '.' }
                    text.toDoubleOrNull() ?: fail("bad number '$text'")
                    "$text::float8"
                }
                c.isLetter() || c == '_' -> {
                    val name = take { it.isLetterOrDigit() || it == '_' }
                    if (name !in names) fail("'$name' is not a number measure")
                    "(vals->>'$name')::float8"
                }
                else -> fail("unexpected '$c'")
            }
        }

        fun peek(): Char? {
            skip()
            return s.getOrNull(i)
        }

        fun take(ok: (Char) -> Boolean): String {
            val start = i
            while (i < s.length && ok(s[i])) i++
            return s.substring(start, i)
        }

        fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg in '$s'")
    }
}
