package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.DashboardShares
import com.anjo.statisticservice.db.Db
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select

object DashboardShareRepository {
    fun userIds(dashboardId: String): List<Long> =
        DashboardShares
            .select(DashboardShares.userId)
            .where { of(dashboardId) }
            .orderBy(DashboardShares.userId)
            .map { it[DashboardShares.userId] }

    fun replace(
        dashboardId: String,
        userIds: List<Long>,
    ) {
        delete(dashboardId)
        DashboardShares.batchInsert(userIds.distinct()) { u ->
            this[DashboardShares.orgId] = Db.ORG
            this[DashboardShares.dashboardId] = dashboardId
            this[DashboardShares.userId] = u
        }
    }

    fun delete(dashboardId: String) {
        DashboardShares.deleteWhere { of(dashboardId) }
    }

    private fun of(dashboardId: String) = (DashboardShares.orgId eq Db.ORG) and (DashboardShares.dashboardId eq dashboardId)
}
