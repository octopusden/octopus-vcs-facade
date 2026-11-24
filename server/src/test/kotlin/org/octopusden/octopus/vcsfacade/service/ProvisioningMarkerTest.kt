package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProvisioningMarkerTest {
    @Test
    fun `the key is the one group 8 writes`() {
        assertEquals("Provisioning-Marker", ProvisioningMarker.TRAILER_KEY)
    }

    @Test
    fun `no trailer at all`() {
        assertNull(ProvisioningMarker.parse("Initial content"))
        assertNull(ProvisioningMarker.parse(""))
        assertNull(ProvisioningMarker.parse(null))
    }

    @Test
    fun `the trailer alone in the last paragraph`() {
        assertEquals("m-1", ProvisioningMarker.parse("Initial content\n\nProvisioning-Marker: m-1"))
    }

    @Test
    fun `the trailer among other trailers`() {
        val message = "Initial content\n\nSigned-off-by: A <a@example.org>\nProvisioning-Marker: m-1\nCo-authored-by: B <b@example.org>\n"
        assertEquals("m-1", ProvisioningMarker.parse(message))
    }

    @Test
    fun `other trailers only`() {
        assertNull(ProvisioningMarker.parse("Initial content\n\nSigned-off-by: A <a@example.org>"))
    }

    @Test
    fun `a trailer line that is not in the last paragraph does not count`() {
        assertNull(ProvisioningMarker.parse("Initial content\n\nProvisioning-Marker: m-1\n\nJust some more text"))
    }

    @Test
    fun `the key matches without regard to case`() {
        assertEquals("m-1", ProvisioningMarker.parse("s\n\nprovisioning-marker: m-1"))
        assertEquals("m-1", ProvisioningMarker.parse("s\n\nPROVISIONING-MARKER: m-1"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("m-1", ProvisioningMarker.parse("s\n\nProvisioning-Marker:   m-1  \t\n\n"))
        assertEquals("m-1", ProvisioningMarker.parse("s\n\nProvisioning-Marker:m-1"))
    }

    @Test
    fun `carriage return line endings are handled`() {
        assertEquals("m-1", ProvisioningMarker.parse("Initial content\r\n\r\nSigned-off-by: A\r\nProvisioning-Marker: m-1\r\n"))
    }

    @Test
    fun `a value with a colon keeps it`() {
        assertEquals("a:b c", ProvisioningMarker.parse("s\n\nProvisioning-Marker: a:b c"))
    }

    @Test
    fun `an empty value is no marker`() {
        assertNull(ProvisioningMarker.parse("s\n\nProvisioning-Marker:   "))
    }

    @Test
    fun `a key that only contains the marker key does not match`() {
        assertNull(ProvisioningMarker.parse("s\n\nX-Provisioning-Marker: m-1"))
    }
}
