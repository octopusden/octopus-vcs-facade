package org.octopusden.octopus.vcsfacade.client

import feign.FeignException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.vcsfacade.client.common.dto.GroupExistence
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

class ProvisioningReadClientTest {
    private val server = RecordingServer()
    private var token: String? = null

    private val client = ClassicVcsFacadeClient(
        object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = server.url

            override fun getTimeRetryInMillis() = 10

            override fun getBearerToken() = token
        },
    )

    @AfterEach
    fun tearDown() = server.close()

    private fun lastRequest() = server.requests.last()

    @Test
    fun `group existence uses its path and query`() {
        server.responseBody = """{"exists":true}"""
        assertEquals(GroupExistence(true), client.getGroupExistence(SSH_URL))
        assertEquals("GET", lastRequest().method)
        assertEquals("/rest/api/2/groups", lastRequest().path)
        assertEquals("sshUrl=ssh%3A%2F%2Fgit%40host%2Fgroup%2Frepo.git", lastRequest().query)
    }

    @Test
    fun `repository state uses its path and decodes every field`() {
        server.responseBody =
            """{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":true,"rootCommitMarker":"r"}"""
        assertEquals(RepositoryState(RepositoryStatus.PRESENT, "m", "main", true, "r"), client.getRepositoryState(SSH_URL))
        assertEquals("GET", lastRequest().method)
        assertEquals("/rest/api/2/repository/state", lastRequest().path)
        assertEquals("sshUrl=ssh%3A%2F%2Fgit%40host%2Fgroup%2Frepo.git", lastRequest().query)
    }

    @Test
    fun `repository state decodes a missing repository`() {
        server.responseBody = """{"status":"MISSING","marker":null,"defaultBranch":null,"hasCommits":false,"rootCommitMarker":null}"""
        assertEquals(RepositoryState(RepositoryStatus.MISSING), client.getRepositoryState(SSH_URL))
    }

    @Test
    fun `bearer token is sent when set and re-read on every request`() {
        server.responseBody = """{"exists":false}"""
        token = "first"
        client.getGroupExistence(SSH_URL)
        assertEquals("Bearer first", lastRequest().headers["authorization"])
        token = "second"
        client.getGroupExistence(SSH_URL)
        assertEquals("Bearer second", lastRequest().headers["authorization"])
    }

    @Test
    fun `no authorization header without a token`() {
        server.responseBody = """{"exists":false}"""
        client.getGroupExistence(SSH_URL)
        assertNull(lastRequest().headers["authorization"])
    }

    @Test
    fun `a provider without the token member still works`() {
        val legacy = ClassicVcsFacadeClient(
            object : VcsFacadeClientParametersProvider {
                override fun getApiUrl() = server.url

                override fun getTimeRetryInMillis() = 10
            },
        )
        server.responseBody = """{"exists":false}"""
        legacy.getGroupExistence(SSH_URL)
        assertNull(lastRequest().headers["authorization"])
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource(
        "INVALID_REQUEST,400,INVALID_REQUEST",
        "TARGET_NOT_ALLOWED,403,TARGET_NOT_ALLOWED",
        "NOT_FOUND,404,NOT_FOUND",
    )
    fun `error codes decode to their exception`(
        code: String,
        status: Int,
        expected: String,
    ) {
        server.status = status
        server.responseBody = """{"errorCode":"$code","errorMessage":"boom"}"""
        val exception = assertThrows(Exception::class.java) { client.getRepositoryState(SSH_URL) }
        val expectedType = when (expected) {
            "INVALID_REQUEST" -> InvalidRequestException::class.java
            "TARGET_NOT_ALLOWED" -> TargetNotAllowedException::class.java
            else -> NotFoundException::class.java
        }
        assertEquals(expectedType, exception.javaClass)
        assertEquals("boom", exception.message)
    }

    @Test
    fun `an unknown error code still decodes as a plain feign error`() {
        server.status = 400
        server.responseBody = """{"errorCode":"SOMETHING_NEWER","errorMessage":"boom"}"""
        val exception = assertThrows(FeignException::class.java) { client.getRepositoryState(SSH_URL) }
        assertEquals(400, exception.status())
    }

    companion object {
        private const val SSH_URL = "ssh://git@host/group/repo.git"
    }
}
