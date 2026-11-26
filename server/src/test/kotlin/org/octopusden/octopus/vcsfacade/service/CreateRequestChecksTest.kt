package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException

class CreateRequestChecksTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = [
            " ", "\t", "  \n ",
            "line one\nline two", "line one\r\nline two", "x\ry", "trailing\n",
            " m1", "m1 ", " m1 ", "\tm1", "m1\t", " m1",
        ],
    )
    fun `a blank, multi-line or whitespace-padded marker is rejected`(marker: String?) {
        assertThrows(InvalidRequestException::class.java) { CreateRequestChecks.validate(marker, "main") }
    }

    @Test
    fun `a marker over 255 characters is rejected, 255 is fine`() {
        val exception = assertThrows(InvalidRequestException::class.java) { CreateRequestChecks.validate("m".repeat(256), "main") }
        assertFalse(exception.message.orEmpty().contains("m".repeat(10)), "the message repeats the marker")
        assertEquals("m".repeat(255) to "main", CreateRequestChecks.validate("m".repeat(255), "main"))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "\t"])
    fun `a missing or blank branch is rejected`(branch: String?) {
        assertThrows(InvalidRequestException::class.java) { CreateRequestChecks.validate("m1", branch) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["main", "develop", "feature/new-thing", "release-1.2", "v1.0"])
    fun `valid branches pass`(branch: String) {
        assertEquals("m1" to branch, CreateRequestChecks.validate("m1", branch))
    }

    @Test
    fun `whitespace inside the marker is fine`() {
        assertEquals("a b  c" to "main", CreateRequestChecks.validate("a b  c", "main"))
    }
}
