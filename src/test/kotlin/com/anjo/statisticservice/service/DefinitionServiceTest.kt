package com.anjo.statisticservice.service

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Bundle
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Dimension
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.FieldSpec
import com.anjo.statisticservice.model.Measure
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Rule
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.model.Variable
import com.anjo.statisticservice.model.Widget
import com.anjo.statisticservice.model.WidgetOptions
import com.anjo.statisticservice.model.dto.ImportResult
import com.anjo.statisticservice.testutils.Pg
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant

class DefinitionServiceTest :
    FunSpec({
        val db = Pg.db()
        val status = SourceStatusService()
        val defs = DefinitionService(db, status)
        val users = UserService(db)
        val climate =
            Dataset(
                id = "climate",
                measures = listOf(Measure("temp"), Measure("hum"), Measure("gen")),
                dimensions = listOf(Dimension("device")),
            )
        val other = Dataset(id = "other", measures = listOf(Measure("temp")), dimensions = listOf(Dimension("device")))

        beforeTest { Pg.truncateAll() }

        afterSpec { db.close() }

        suspend fun principal(
            email: String,
            role: String,
        ) = users.createUser(email, "", role, "password-123").let { UserPrincipal(it.id, it.role, "token") }

        fun widget(
            id: String,
            dataset: String,
            vararg measures: QueryMeasure,
            options: WidgetOptions = WidgetOptions(),
        ) = Widget(id = id, type = "line", query = Query(dataset, measures.toList()), options = options)

        suspend fun failure(block: suspend () -> Unit) = shouldThrow<ApiException> { block() }

        suspend fun seed(admin: UserPrincipal) {
            defs.putDataset(climate, force = false)
            defs.putDataset(other, force = false)
            defs.putSource(Source(id = "pico", dataset = "climate", mapping = listOf(Rule(vals = mapOf("hum" to FieldSpec(path = "h"))))))
            defs.putSource(
                Source(id = "elsewhere", dataset = "other", mapping = listOf(Rule(vals = mapOf("temp" to FieldSpec(path = "t"))))),
            )
            defs.putDashboard(
                admin,
                Dashboard(
                    id = "home",
                    visibility = "all",
                    variables =
                        listOf(
                            Variable("dev", "dimension", dataset = "climate", dimension = "device"),
                            Variable("range", "timeRange"),
                        ),
                    widgets =
                        listOf(
                            widget("t", "climate", QueryMeasure("temp")),
                            widget("g", "climate", QueryMeasure("temp", "at_max", by = "gen"), options = WidgetOptions(x = "device")),
                            widget("o", "other", QueryMeasure("temp")),
                        ),
                ),
            )
        }

        test("removing a referenced name needs force, other changes save directly") {
            seed(principal("admin@x.io", ADMIN))
            failure { defs.putDataset(climate.copy(measures = listOf(Measure("hum"), Measure("gen"))), force = false) }.details shouldBe
                listOf("home/t", "home/g")
            failure { defs.putDataset(climate.copy(measures = listOf(Measure("temp"), Measure("hum"))), force = false) }.details shouldBe
                listOf("home/g")
            failure { defs.putDataset(climate.copy(dimensions = emptyList()), force = false) }.details shouldBe
                listOf("home/g", "home/\$dev")
            failure { defs.putDataset(climate.copy(measures = listOf(Measure("temp"), Measure("gen"))), force = false) }.let {
                it.status shouldBe HttpStatusCode.Conflict
                it.details shouldBe listOf("source pico")
            }
            defs.putDataset(climate.copy(measures = climate.measures + Measure("extra")), force = false)
            defs.putDataset(climate.copy(measures = listOf(Measure("hum"), Measure("gen"))), force = true)
            defs.dataset("climate")?.measures?.map { it.name } shouldBe listOf("hum", "gen")
            defs.putDataset(other.copy(dimensions = emptyList(), measures = listOf(Measure("temp"), Measure("unused"))), force = false)
            defs.putDataset(other.copy(dimensions = emptyList(), measures = listOf(Measure("temp"))), force = false)
        }

        test("a dataset in use is deleted only with force, unknown definitions are not found") {
            seed(principal("admin@x.io", ADMIN))
            failure { defs.deleteDataset("climate", force = false) }.details shouldBe
                listOf("home/t", "home/g", "home/\$dev", "source pico")
            failure { defs.deleteDataset("missing", force = false) }.status shouldBe HttpStatusCode.NotFound
            defs.deleteDataset("climate", force = true)
            defs.dataset("climate") shouldBe null
            failure { defs.deleteSource("missing") }.message shouldBe "Unknown source 'missing'"
            status.accepted("pico", 1, Instant.parse("2026-10-27T12:00:00Z"))
            defs.deleteSource("pico")
            defs.source("pico") shouldBe null
            status.view(Source(id = "pico", dataset = "climate")).state shouldBe "waiting-for-data"
        }

        test("a source without a credential accepts no token, a rotated one only its own") {
            seed(principal("admin@x.io", ADMIN))
            defs.verifyCredential("pico", "anything") shouldBe false
            val token = defs.rotateCredential("pico")
            defs.verifyCredential("pico", token) shouldBe true
            defs.verifyCredential("pico", token + "x") shouldBe false
            failure { defs.rotateCredential("missing") }.status shouldBe HttpStatusCode.NotFound
        }

        test("only the owning editor or an admin changes, shares or deletes a dashboard") {
            val admin = principal("admin@x.io", ADMIN)
            val owner = principal("owner@x.io", EDITOR)
            val stranger = principal("stranger@x.io", EDITOR)
            val viewer = principal("viewer@x.io", VIEWER)
            seed(admin)
            val mine = Dashboard(id = "mine", visibility = "users", widgets = listOf(widget("t", "climate", QueryMeasure("temp"))))
            defs.putDashboard(owner, mine)
            defs.putDashboard(owner, mine.copy(title = "Mine"))
            failure { defs.putDashboard(stranger, mine) }.message shouldBe "Only the owner or an admin can change 'mine'"
            failure { defs.putDashboard(viewer, mine) }.status shouldBe HttpStatusCode.Forbidden
            failure { defs.putDashboard(owner, Dashboard(id = "home")) }.status shouldBe HttpStatusCode.Forbidden
            defs.putDashboard(admin, mine.copy(title = "By admin"))

            failure { defs.dashboardDocument(viewer, "mine") }.status shouldBe HttpStatusCode.Forbidden
            defs.shares(owner, "mine") shouldBe emptyList()
            defs.setShares(owner, "mine", listOf(viewer.id))
            defs.shares(admin, "mine") shouldBe listOf(viewer.id)
            defs.dashboardDocument(viewer, "mine").toString() shouldBe
                defs.dashboardDocument(admin, "mine").toString()
            failure { defs.shares(stranger, "mine") }.message shouldBe "Not allowed for your role"
            failure { defs.setShares(owner, "nowhere", emptyList()) }.status shouldBe HttpStatusCode.NotFound

            failure { defs.deleteDashboard(stranger, "mine") }.message shouldBe "Only the owner or an admin can delete 'mine'"
            defs.deleteDashboard(owner, "mine")
            failure { defs.deleteDashboard(admin, "mine") }.message shouldBe "Unknown dashboard 'mine'"
        }

        test("dashboards may only chart datasets that exist") {
            val admin = principal("admin@x.io", ADMIN)
            seed(admin)
            val e =
                failure {
                    defs.putDashboard(
                        admin,
                        Dashboard(id = "lost", widgets = listOf(widget("a", "nowhere"), widget("b", "nowhere"), widget("c", "climate"))),
                    )
                }
            e.message shouldBe "Unknown dataset(s) in widgets"
            e.details shouldBe listOf("nowhere")
        }

        test("viewers reach only datasets used by dashboards they can see") {
            val admin = principal("admin@x.io", ADMIN)
            val viewer = principal("viewer@x.io", VIEWER)
            defs.putDataset(climate, force = false)
            defs.putDataset(other, force = false)
            failure { defs.accessibleDataset(viewer, "climate") }.status shouldBe HttpStatusCode.Forbidden
            defs.putDashboard(
                admin,
                Dashboard(id = "pub", visibility = "all", widgets = listOf(widget("t", "climate", QueryMeasure("temp")))),
            )
            defs.accessibleDataset(viewer, "climate") shouldBe climate
            defs.queryableDataset(viewer, "climate") shouldBe climate
            failure { defs.datasetDocument(viewer, "other") }.status shouldBe HttpStatusCode.Forbidden
            failure { defs.accessibleDataset(admin, "missing") }.status shouldBe HttpStatusCode.NotFound
            failure { defs.queryableDataset(admin, "missing") }.status shouldBe HttpStatusCode.BadRequest
            val exported = defs.export(viewer)
            exported.datasets shouldBe listOf(climate)
            exported.sources shouldBe emptyList()
            exported.dashboards.map { it.id } shouldBe listOf("pub")
            defs.export(admin).datasets shouldContainExactlyInAnyOrder listOf(climate, other)
        }

        test("an import with another bundle version changes nothing; imported dashboards have no owner") {
            val editor = principal("editor@x.io", EDITOR)
            defs.import(Bundle(version = 2, datasets = listOf(climate))) shouldBe ImportResult(0, 0, listOf("version: must be 1"))
            defs.datasets() shouldBe emptyList()
            defs.import(Bundle(datasets = listOf(climate), dashboards = listOf(Dashboard(id = "shared", visibility = "all")))) shouldBe
                ImportResult(2, 0)
            failure { defs.putDashboard(editor, Dashboard(id = "shared")) }.status shouldBe HttpStatusCode.Forbidden
        }
    })
