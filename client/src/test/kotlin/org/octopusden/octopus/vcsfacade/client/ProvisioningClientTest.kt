package org.octopusden.octopus.vcsfacade.client

import feign.FeignException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateRepositoryResult
import org.octopusden.octopus.vcsfacade.client.common.dto.GroupExistence
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryConflictException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryEmptyException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryExistsException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

class ProvisioningClientTest {
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

    private fun lastBody() = String(lastRequest().body, Charsets.ISO_8859_1)

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
        assertTrue(lastRequest().query!!.startsWith("sshUrl="))
    }

    @Test
    fun `repository state decodes a missing repository`() {
        server.responseBody = """{"status":"MISSING","marker":null,"defaultBranch":null,"hasCommits":false,"rootCommitMarker":null}"""
        assertEquals(RepositoryState(RepositoryStatus.MISSING), client.getRepositoryState(SSH_URL))
    }

    @Test
    fun `create sends every part as multipart`() {
        server.status = 201
        server.responseBody = """{"created":true,"state":{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":true}}"""
        val result = client.createRepository(SSH_URL, "m", "main", "first", "zip-bytes".toByteArray())
        assertEquals(CreateRepositoryResult(true, RepositoryState(RepositoryStatus.PRESENT, "m", "main", true)), result)
        assertEquals("POST", lastRequest().method)
        assertEquals("/rest/api/2/repository", lastRequest().path)
        assertTrue(lastRequest().query!!.startsWith("sshUrl="))
        assertTrue(lastRequest().headers.getValue("content-type").startsWith("multipart/form-data"))
        val body = lastBody()
        assertTrue(Regex("""name="marker"\r\n(?:[^\r\n]+\r\n)*\r\nm\r\n""").containsMatchIn(body), body)
        assertTrue(Regex("""name="branch"\r\n(?:[^\r\n]+\r\n)*\r\nmain\r\n""").containsMatchIn(body), body)
        assertTrue(Regex("""name="commitMessage"\r\n(?:[^\r\n]+\r\n)*\r\nfirst\r\n""").containsMatchIn(body), body)
        assertTrue(
            Regex(
                """Content-Disposition: form-data; name="content"; filename="content\.zip"\r\n""" +
                    """(?:[^\r\n]+\r\n)*?Content-Type: application/zip\r\n(?:[^\r\n]+\r\n)*\r\nzip-bytes\r\n""",
            ).containsMatchIn(body),
            body,
        )
    }

    @Test
    fun `create leaves out the parts that were not given`() {
        server.responseBody = """{"created":false,"state":{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":false}}"""
        assertFalse(client.createRepository(SSH_URL, "m", "main").created)
        val body = lastBody()
        assertTrue(body.contains("""name="marker""""))
        assertTrue(body.contains("""name="branch""""))
        assertFalse(body.contains("""name="commitMessage""""), body)
        assertFalse(body.contains("""name="content""""), body)
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
        "REPOSITORY_EXISTS,409,REPOSITORY_EXISTS",
        "REPOSITORY_CONFLICT,409,REPOSITORY_CONFLICT",
        "REPOSITORY_EMPTY,502,REPOSITORY_EMPTY",
        "INVALID_CONTENT,400,INVALID_CONTENT",
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
            "REPOSITORY_EXISTS" -> RepositoryExistsException::class.java
            "REPOSITORY_CONFLICT" -> RepositoryConflictException::class.java
            "REPOSITORY_EMPTY" -> RepositoryEmptyException::class.java
            "INVALID_CONTENT" -> InvalidContentException::class.java
            "INVALID_REQUEST" -> InvalidRequestException::class.java
            "TARGET_NOT_ALLOWED" -> TargetNotAllowedException::class.java
            else -> NotFoundException::class.java
        }
        assertEquals(expectedType, exception.javaClass)
        assertEquals("boom", exception.message)
    }

    @Test
    fun `a conflict carries the state found`() {
        server.status = 409
        server.responseBody =
            """{"errorCode":"REPOSITORY_CONFLICT","errorMessage":"boom","repositoryState":""" +
            """{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":true,"rootCommitMarker":"other"}}"""
        val exception = assertThrows(RepositoryConflictException::class.java) { client.createRepository(SSH_URL, "m", "main") }
        assertEquals(RepositoryState(RepositoryStatus.PRESENT, "m", "main", true, "other"), exception.state)
    }

    @Test
    fun `a conflict without a state still decodes`() {
        server.status = 409
        server.responseBody = """{"errorCode":"REPOSITORY_EXISTS","errorMessage":"boom"}"""
        val exception = assertThrows(RepositoryExistsException::class.java) { client.createRepository(SSH_URL, "m", "main") }
        assertNull(exception.state)
    }

    @Test
    fun `an unknown error code decodes as before`() {
        server.status = 418
        server.responseBody = """{"errorCode":"SOMETHING_NEW","errorMessage":"boom"}"""
        assertThrows(FeignException::class.java) { client.getRepositoryState(SSH_URL) }
    }

    companion object {
        private const val SSH_URL = "ssh://git@host/group/repo.git"
    }
}
