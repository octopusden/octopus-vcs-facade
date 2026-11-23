package org.octopusden.octopus.vcsfacade.client.common.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryConflictException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryEmptyException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryExistsException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

class VcsFacadeErrorCodeTest {
    private val state = RepositoryState(RepositoryStatus.PRESENT, "m", "main", true, "other")

    @Test
    fun `each provisioning code maps to its exception`() {
        assertTrue(VcsFacadeErrorCode.REPOSITORY_EXISTS.getException("m") is RepositoryExistsException)
        assertTrue(VcsFacadeErrorCode.REPOSITORY_CONFLICT.getException("m") is RepositoryConflictException)
        assertTrue(VcsFacadeErrorCode.REPOSITORY_EMPTY.getException("m") is RepositoryEmptyException)
        assertTrue(VcsFacadeErrorCode.INVALID_CONTENT.getException("m") is InvalidContentException)
        assertTrue(VcsFacadeErrorCode.INVALID_REQUEST.getException("m") is InvalidRequestException)
        assertTrue(VcsFacadeErrorCode.TARGET_NOT_ALLOWED.getException("m") is TargetNotAllowedException)
    }

    @Test
    fun `conflict exceptions carry the state found`() {
        val exists = VcsFacadeErrorCode.REPOSITORY_EXISTS.getException("m", state) as RepositoryExistsException
        val conflict = VcsFacadeErrorCode.REPOSITORY_CONFLICT.getException("m", state) as RepositoryConflictException
        assertEquals(state, exists.state)
        assertEquals(state, conflict.state)
        assertEquals("m", exists.message)
    }

    @Test
    fun `state is optional`() {
        assertEquals(null, (VcsFacadeErrorCode.REPOSITORY_EXISTS.getException("m") as RepositoryExistsException).state)
    }

    @Test
    fun `other codes ignore the state`() {
        val exception = VcsFacadeErrorCode.OTHER.getException("m", state)
        assertSame(VcsFacadeException::class.java, exception.javaClass)
    }
}
