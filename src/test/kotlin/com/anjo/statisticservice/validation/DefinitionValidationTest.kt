package com.anjo.statisticservice.validation

import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.Channel
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.Dimension
import com.anjo.statisticservice.model.FieldSpec
import com.anjo.statisticservice.model.Measure
import com.anjo.statisticservice.model.MeasureType
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.QueryMeasure
import com.anjo.statisticservice.model.Rule
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.TimestampSpec
import com.anjo.statisticservice.model.Variable
import com.anjo.statisticservice.model.Widget
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.JsonPrimitive

class DefinitionValidationTest :
    FunSpec({
        val ds =
            Dataset(
                id = "climate",
                label = "Climate",
                measures =
                    listOf(
                        Measure("temp", label = "Temperature"),
                        Measure("on", MeasureType.BOOLEAN),
                        Measure("note", MeasureType.TEXT),
                    ),
                dimensions = listOf(Dimension("device", label = "Device")),
                calculated = mapOf("double" to "temp * 2"),
                retentionDays = 30,
            )

        fun widget(
            id: String,
            query: Query = Query("climate"),
        ) = Widget(id = id, type = "line", query = query)

        fun queryReasons(
            vararg m: QueryMeasure,
            bucket: String = "1h",
            groupBy: List<String> = emptyList(),
            filter: Map<String, List<String>> = emptyMap(),
        ) = shouldThrow<ApiException> { validateQuery(Query("climate", m.toList(), filter, groupBy, bucket = bucket), ds) }.details

        test("a valid dataset has no violations") {
            datasetViolations(ds).shouldBeEmpty()
            datasetViolations(Dataset(id = "plain", measures = listOf(Measure("x")))).shouldBeEmpty()
        }

        test("dataset names must be well formed, unique and calculated measures must parse") {
            datasetViolations(
                ds.copy(
                    id = "9bad id",
                    measures = listOf(Measure("temp"), Measure("temp"), Measure("bad-name", label = " ")),
                    calculated = mapOf("broken" to "temp +", "unknown" to "pressure * 2"),
                    retentionDays = 0,
                ),
            ) shouldContainExactlyInAnyOrder
                listOf(
                    "id: '9bad id' must match ^[A-Za-z_][A-Za-z0-9_-]{0,62}$",
                    "bad-name.label: must not be blank",
                    "name: 'bad-name' must match ^[A-Za-z_][A-Za-z0-9_]{0,62}$",
                    "name: 'temp' is declared more than once",
                    "retentionDays: must be > 0",
                    "calculated.broken: unexpected end in 'temp +'",
                    "calculated.unknown: 'pressure' is not a number measure in 'pressure * 2'",
                )
        }

        test("a valid source has no violations, every broken rule is listed otherwise") {
            val source =
                Source(
                    id = "pico",
                    dataset = "climate",
                    mapping =
                        listOf(
                            Rule(
                                timestamp = TimestampSpec("ts", "epoch_s"),
                                dims = mapOf("device" to FieldSpec(path = "id")),
                                vals = mapOf("temp" to FieldSpec(constValue = JsonPrimitive(1))),
                            ),
                            Rule(vals = mapOf("on" to FieldSpec(path = "on"))),
                        ),
                )
            sourceViolations(source).shouldBeEmpty()
            sourceViolations(
                source.copy(
                    version = 2,
                    id = "",
                    channel = Channel("poll"),
                    rateLimitPerSecond = 0,
                    silentAfterMinutes = 0,
                    mapping =
                        listOf(
                            Rule(
                                timestamp = TimestampSpec("ts", "unix"),
                                dims = mapOf("device" to FieldSpec()),
                                vals = mapOf("temp" to FieldSpec(path = "t", constValue = JsonPrimitive(1))),
                            ),
                        ),
                ),
            ) shouldContainExactlyInAnyOrder
                listOf(
                    "version: must be 1",
                    "id: '' must match ^[A-Za-z_][A-Za-z0-9_-]{0,62}$",
                    "channel.type: 'poll' not supported yet",
                    "rateLimitPerSecond: must be > 0",
                    "silentAfterMinutes: must be > 0",
                    "mapping[0].device: exactly one of path/const",
                    "mapping[0].temp: exactly one of path/const",
                    "mapping[0].timestamp.format: iso | epoch_ms | epoch_s",
                )
        }

        test("a source may only map names its dataset declares") {
            shouldNotThrowAny { validateSource(Source(id = "pico", dataset = "climate"), ds) }
            val e =
                shouldThrow<ApiException> {
                    validateSource(
                        Source(
                            id = "pico",
                            dataset = "climate",
                            mapping =
                                listOf(
                                    Rule(
                                        dims = mapOf("room" to FieldSpec(path = "room")),
                                        vals = mapOf("pressure" to FieldSpec(path = "p")),
                                        constDims = mapOf("site" to "home", "device" to "pico"),
                                    ),
                                ),
                        ),
                        ds,
                    )
                }
            e.message shouldBe "Invalid source"
            e.details shouldContainExactlyInAnyOrder
                listOf(
                    "mapping[0]: dimension 'room' not declared in dataset",
                    "mapping[0]: dimension 'site' not declared in dataset",
                    "mapping[0]: measure 'pressure' not declared in dataset",
                )
        }

        test("dashboards check visibility, refresh, variables and widget layout") {
            val dashboard =
                Dashboard(
                    id = "home",
                    visibility = "users",
                    refreshSeconds = 5,
                    variables = listOf(Variable("range", "timeRange"), Variable("device", "dimension")),
                    widgets = listOf(widget("a"), widget("b").copy(w = 12, h = 1)),
                )
            dashboardViolations(dashboard).shouldBeEmpty()
            dashboardViolations(dashboard.copy(refreshSeconds = null)).shouldBeEmpty()
            dashboardViolations(
                dashboard.copy(
                    version = 0,
                    id = "-home",
                    visibility = "public",
                    refreshSeconds = 4,
                    variables = listOf(Variable("from", "timeRange"), Variable("to", "timeRange")),
                    widgets = listOf(widget("a").copy(type = "pie", w = 0, h = 0), widget("a").copy(w = 13)),
                ),
            ) shouldContainExactlyInAnyOrder
                listOf(
                    "version: must be 1",
                    "id: '-home' must match ^[A-Za-z_][A-Za-z0-9_-]{0,62}$",
                    "visibility: private | users | all",
                    "refreshSeconds: null or >= 5",
                    "variables: at most one timeRange",
                    "widget a: type must be one of [line, area, bar, stat, gauge, table]",
                    "widget a: w must be 1..12",
                    "widget a: h must be >= 1",
                    "widget a: w must be 1..12",
                    "widgets: ids must be unique",
                )
            shouldThrow<ApiException> { validateDashboard(dashboard.copy(visibility = "public")) }.message shouldBe "Invalid dashboard"
            shouldThrow<ApiException> { validateDataset(Dataset(id = "empty")) }.details shouldBe
                listOf("measures: at least one is required")
        }

        test("queries may aggregate known measures in ways their type allows") {
            shouldNotThrowAny {
                validateQuery(
                    Query(
                        "climate",
                        listOf(
                            QueryMeasure("temp", "avg"),
                            QueryMeasure("double", "sum"),
                            QueryMeasure("ts", "max"),
                            QueryMeasure("note", "at_max", by = "temp"),
                            QueryMeasure("on"),
                        ),
                        groupBy = listOf("device"),
                        filter = mapOf("device" to listOf("a")),
                    ),
                    ds,
                )
                validateQuery(Query("climate", listOf(QueryMeasure("note", "median")), bucket = "raw"), ds)
            }
            queryReasons(QueryMeasure("pressure", "avg"), groupBy = listOf("room"), filter = mapOf("site" to listOf("x"))) shouldBe
                listOf("Unknown dimension 'room'", "Unknown dimension 'site'", "Unknown measure 'pressure'")
            queryReasons(QueryMeasure("note", "median")).single() shouldStartWith "agg: count | sum"
            queryReasons(QueryMeasure("on", "sum")) shouldBe listOf("agg 'sum' needs a number measure: 'on'")
            queryReasons(QueryMeasure("ts", "avg")) shouldBe
                listOf("agg 'avg' needs a number measure: 'ts'", "agg 'avg' not allowed on ts")
            queryReasons(QueryMeasure("temp", "at_min")) shouldBe listOf("agg 'at_min' needs 'by'")
            queryReasons(QueryMeasure("temp", "at_max", by = "note")) shouldBe listOf("'by' must be a number measure: 'note'")
            queryReasons(QueryMeasure("temp", "at_max", by = "nothing")) shouldBe listOf("'by' must be a number measure: 'nothing'")
        }
    })
