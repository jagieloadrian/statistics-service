package com.anjo.statisticservice.db

import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.stringParam

class Sql(
    private val sql: String,
) : Expression<Any?>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(sql)
    }
}

class DimText(
    private val name: String,
) : ExpressionWithColumnType<String?>() {
    override val columnType = TextColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(Records.dims, "->>'", name, "'")
    }
}

class ILike(
    expr: Expression<*>,
    pattern: String,
) : ComparisonOp(expr, stringParam(pattern), "ILIKE")
