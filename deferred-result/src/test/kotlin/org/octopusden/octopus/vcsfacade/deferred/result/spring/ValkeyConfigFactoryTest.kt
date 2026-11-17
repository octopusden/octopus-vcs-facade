package org.octopusden.octopus.vcsfacade.deferred.result.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValkeyConfigFactoryTest {
    @Test
    fun buildsSingleServerConfigByDefault() {
        val config = ValkeyConfigFactory.create(DeferredResultProperties.Valkey())

        assertTrue(config.isSingleConfig)
        assertFalse(config.isSentinelConfig)
        assertEquals("valkey://localhost:6379", config.useSingleServer().address)
    }
}
