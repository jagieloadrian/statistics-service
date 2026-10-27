package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import java.sql.Connection

object DashboardShareRepository {

    fun userIds(c: Connection, dashboardId: String): List<Long> =
        c.query("SELECT user_id FROM dashboard_shares WHERE org_id = ? AND dashboard_id = ? ORDER BY user_id", Db.ORG, dashboardId) { it.getLong(1) }

    fun replace(c: Connection, dashboardId: String, userIds: List<Long>) {
        delete(c, dashboardId)
        userIds.distinct().forEach { c.update("INSERT INTO dashboard_shares (org_id, dashboard_id, user_id) VALUES (?, ?, ?)", Db.ORG, dashboardId, it) }
    }

    fun delete(c: Connection, dashboardId: String) {
        c.update("DELETE FROM dashboard_shares WHERE org_id = ? AND dashboard_id = ?", Db.ORG, dashboardId)
    }
}
