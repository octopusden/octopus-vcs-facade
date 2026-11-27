package org.octopusden.octopus.vcsfacade.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.util.unit.DataSize
import org.springframework.web.multipart.support.StandardServletMultipartResolver

class ProvisioningConfigTest {
    @Test
    fun `the multipart limits follow max-content-size`() {
        val limits = ProvisioningConfig().multipartConfigElement(ProvisioningProperties(maxContentSize = DataSize.ofMegabytes(10)))
        assertEquals(DataSize.ofMegabytes(10).toBytes(), limits.maxFileSize)
        // room for the text parts and the multipart framing, nothing more
        assertEquals(DataSize.ofMegabytes(10).toBytes() + 64 * 1024, limits.maxRequestSize)
    }

    @Test
    fun `the default limit is 50 MB`() {
        val limits = ProvisioningConfig().multipartConfigElement(ProvisioningProperties())
        assertEquals(DataSize.ofMegabytes(50).toBytes(), limits.maxFileSize)
    }

    @Test
    fun `the multipart resolver parses lazily`() {
        val resolver = ProvisioningConfig().multipartResolver()
        assertEquals(StandardServletMultipartResolver::class.java, resolver.javaClass)
        assertTrue(ReflectionTestUtils.getField(resolver, "resolveLazily") as Boolean)
    }
}
