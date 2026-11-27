package org.octopusden.octopus.vcsfacade.client

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateRepositoryResult
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

class ProvisioningCreateClientTest {
    private val server = RecordingServer()

    private val client = ClassicVcsFacadeClient(
        object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = server.url

            override fun getTimeRetryInMillis() = 10
        },
    )

    @AfterEach
    fun tearDown() = server.close()

    private fun lastRequest() = server.requests.last()

    // Latin-1 keeps every byte of the zip as one character, so a part's bytes can be compared as a string.
    private fun lastBody() = String(lastRequest().body, Charsets.ISO_8859_1)

    @Test
    fun `create sends the query and every part as multipart with the zip as a file part`() {
        server.status = 201
        server.responseBody = """{"created":true,"state":{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":true}}"""
        val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00, 0x7f, 0x0a)
        val result = client.createRepository(SSH_URL, "m", "main", "first", zip)
        assertEquals(CreateRepositoryResult(true, RepositoryState(RepositoryStatus.PRESENT, "m", "main", true)), result)
        assertEquals("POST", lastRequest().method)
        assertEquals("/rest/api/2/repository", lastRequest().path)
        assertEquals("sshUrl=ssh%3A%2F%2Fgit%40host%2Fgroup%2Frepo.git", lastRequest().query)
        assertTrue(lastRequest().headers.getValue("content-type").startsWith("multipart/form-data"))
        val body = lastBody()
        assertEquals("m", textPart(body, "marker"))
        assertEquals("main", textPart(body, "branch"))
        assertEquals("first", textPart(body, "commitMessage"))
        val file = Regex(
            """Content-Disposition: form-data; name="content"; filename="content\.zip"\r\n""" +
                """(?:[^\r\n]+\r\n)*?Content-Type: application/zip\r\n(?:[^\r\n]+\r\n)*\r\n(.*)\r\n--""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(body)
        assertEquals(String(zip, Charsets.ISO_8859_1), file?.groupValues?.get(1), body)
    }

    @Test
    fun `create leaves out the parts that were not given`() {
        server.responseBody = """{"created":false,"state":{"status":"PRESENT","marker":"m","defaultBranch":"main","hasCommits":false}}"""
        assertFalse(client.createRepository(SSH_URL, "m", "main").created)
        assertEquals("/rest/api/2/repository", lastRequest().path)
        val body = lastBody()
        assertEquals("m", textPart(body, "marker"))
        assertEquals("main", textPart(body, "branch"))
        assertFalse(body.contains("""name="commitMessage""""), body)
        assertFalse(body.contains("""name="content""""), body)
    }

    @Test
    fun `an INVALID_CONTENT answer decodes to its exception`() {
        server.status = 400
        server.responseBody = """{"errorCode":"INVALID_CONTENT","errorMessage":"entry .git/config is not allowed"}"""
        val exception = assertThrows(InvalidContentException::class.java) {
            client.createRepository(SSH_URL, "m", "main", null, byteArrayOf(1))
        }
        assertEquals("entry .git/config is not allowed", exception.message)
    }

    private fun textPart(
        body: String,
        name: String,
    ) = Regex("""name="$name"\r\n(?:[^\r\n]+\r\n)*\r\n([^\r\n]*)\r\n""").find(body)?.groupValues?.get(1)

    companion object {
        private const val SSH_URL = "ssh://git@host/group/repo.git"
    }
}
