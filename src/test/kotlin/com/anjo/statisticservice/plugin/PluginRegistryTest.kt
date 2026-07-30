package com.anjo.statisticservice.plugin

import com.anjo.statisticservice.exception.EmptyDataException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private class FakePlugin(override val id: String) : StatPlugin {
    override suspend fun expose(): PluginRouteSet = PluginRouteSet()
}

class PluginRegistryTest {

    @Test
    fun `resolves a registered plugin by id`() {
        val epidemic = FakePlugin("epidemic")
        val registry = PluginRegistry(listOf(epidemic, FakePlugin("temperature")))

        registry.resolve("epidemic") shouldBe epidemic
    }

    @Test
    fun `throws EmptyDataException for an unknown plugin id`() {
        val registry = PluginRegistry(listOf(FakePlugin("epidemic")))

        shouldThrow<EmptyDataException> { registry.resolve("unknown") }
    }

    @Test
    fun `fails construction on duplicate plugin ids`() {
        shouldThrow<IllegalArgumentException> {
            PluginRegistry(listOf(FakePlugin("epidemic"), FakePlugin("epidemic")))
        }
    }
}
