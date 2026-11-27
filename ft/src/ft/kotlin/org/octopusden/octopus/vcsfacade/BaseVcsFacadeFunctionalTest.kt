package org.octopusden.octopus.vcsfacade

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Date

abstract class BaseVcsFacadeFunctionalTest(
    testService: TestService,
    testClient: TestClient,
) : BaseVcsFacadeTestExtended(testService, testClient) {
    override fun getRepository(sshUrl: String) = client.getRepository(sshUrl)

    override fun createPullRequest(
        sshUrl: String,
        createPullRequest: CreatePullRequest,
    ) = client.createPullRequest(sshUrl, createPullRequest)

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
    ) = client.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitFilesLimit: Int?,
    ) = client.getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef, commitFilesLimit)

    override fun getCommit(
        sshUrl: String,
        hashOrRef: String,
    ) = client.getCommit(sshUrl, hashOrRef)

    override fun getCommitWithFiles(
        sshUrl: String,
        hashOrRef: String,
        commitFilesLimit: Int?,
    ) = client.getCommitWithFiles(sshUrl, hashOrRef, commitFilesLimit)

    override fun getIssuesFromCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
    ) = client.getIssuesFromCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getTags(
        sshUrl: String,
        names: Set<String>?,
    ) = client.getTags(sshUrl, names)

    override fun createTag(
        sshUrl: String,
        createTag: CreateTag,
    ) = client.createTag(sshUrl, createTag)

    override fun getTag(
        sshUrl: String,
        name: String,
    ) = client.getTag(sshUrl, name)

    override fun deleteTag(
        sshUrl: String,
        name: String,
    ) = client.deleteTag(sshUrl, name)

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) = client.searchIssuesInRanges(searchRequest)

    override fun findByIssueKeys(issueKeys: Set<String>) = client.findByIssueKeys(issueKeys)

    override fun findBranchesByIssueKeys(issueKeys: Set<String>) = client.findBranchesByIssueKeys(issueKeys)

    override fun findCommitsByIssueKeys(issueKeys: Set<String>) = client.findCommitsByIssueKeys(issueKeys)

    override fun findCommitsWithFilesByIssueKeys(
        issueKeys: Set<String>,
        commitFilesLimit: Int?,
    ) = client.findCommitsWithFilesByIssueKeys(issueKeys, commitFilesLimit)

    override fun findPullRequestsByIssueKeys(issueKeys: Set<String>) = client.findPullRequestsByIssueKeys(issueKeys)

    // <editor-fold defaultstate="collapsed" desc="repository provisioning reads">
    // Existing endpoints stay open with provisioning on: every other test of this class calls them without a token.

    @Test
    fun groupExistsTest() {
        Assertions.assertTrue(provisioningClient.getGroupExistence(testService.sshUrl(GROUP, REPOSITORY)).exists)
    }

    @Test
    fun groupMissingTest() {
        Assertions.assertFalse(provisioningClient.getGroupExistence(testService.sshUrl(ABSENT_GROUP, "repository")).exists)
    }

    @Test
    fun repositoryStatePresentTest() {
        val state = provisioningClient.getRepositoryState(testService.sshUrl(GROUP, REPOSITORY))
        Assertions.assertEquals(RepositoryStatus.PRESENT, state.status)
        Assertions.assertTrue(state.hasCommits)
        Assertions.assertFalse(state.defaultBranch.isNullOrBlank())
    }

    @Test
    fun repositoryStateMissingRepositoryTest() {
        val state = provisioningClient.getRepositoryState(testService.sshUrl(GROUP, "absent-repository"))
        Assertions.assertEquals(RepositoryStatus.MISSING, state.status)
        Assertions.assertFalse(state.hasCommits)
    }

    @Test
    fun repositoryStateMissingGroupTest() {
        val state = provisioningClient.getRepositoryState(testService.sshUrl(ABSENT_GROUP, "repository"))
        Assertions.assertEquals(RepositoryStatus.MISSING, state.status)
    }

    @Test
    fun provisioningOutsideAllowListTest() {
        val sshUrl = testService.sshUrl(FORBIDDEN_GROUP, "repository")
        Assertions.assertThrows(TargetNotAllowedException::class.java) { provisioningClient.getGroupExistence(sshUrl) }
        Assertions.assertThrows(TargetNotAllowedException::class.java) { provisioningClient.getRepositoryState(sshUrl) }
        PROVISIONING_PATHS.forEach { path ->
            val response = httpGet(path, sshUrl, ProvisioningTestAuth.withPermission())
            Assertions.assertEquals(403, response.statusCode(), "$path: ${response.body()}")
            Assertions.assertTrue(response.body().contains("TARGET_NOT_ALLOWED"), "$path: ${response.body()}")
        }
    }

    @Test
    fun provisioningWithoutTokenTest() {
        PROVISIONING_PATHS.forEach { path ->
            val response = httpGet(path, testService.sshUrl(GROUP, REPOSITORY), null)
            Assertions.assertEquals(401, response.statusCode(), "$path: ${response.body()}")
        }
    }

    @Test
    fun provisioningWithoutPermissionTest() {
        PROVISIONING_PATHS.forEach { path ->
            val response = httpGet(path, testService.sshUrl(GROUP, REPOSITORY), ProvisioningTestAuth.withoutPermission())
            Assertions.assertEquals(403, response.statusCode(), "$path: ${response.body()}")
        }
    }

    @Test
    fun existingEndpointStaysOpenTest() {
        // Through the client without a token, which also waits out a deferred (202) answer.
        val sshUrl = testService.sshUrl(GROUP, REPOSITORY)
        Assertions.assertEquals(sshUrl, client.getRepository(sshUrl).sshUrl)
    }

    private fun httpGet(
        path: String,
        sshUrl: String,
        token: String?,
    ): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest
                .newBuilder()
                .uri(URI("http://$vcsFacadeHost$path?sshUrl=${URLEncoder.encode(sshUrl, StandardCharsets.UTF_8)}"))
                .header("Accept", "application/json")
                .also { builder -> token?.let { builder.header("Authorization", "Bearer $it") } }
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="repository provisioning create checks">
    // The create answers 501 until it is implemented; every rejection below must leave the repository missing.

    @Test
    fun createWithoutBranchTest() {
        val name = "create-no-branch"
        val response = httpCreate(testService.sshUrl(GROUP, name), ProvisioningTestAuth.withPermission(), mapOf("marker" to "m-1"))
        assertRejected(response, 400, "INVALID_REQUEST")
        assertNothingCreated(name)
    }

    @Test
    fun createWithInvalidMarkerTest() {
        val name = "create-bad-marker"
        val sshUrl = testService.sshUrl(GROUP, name)
        listOf("line one\nline two", " padded", "", "m".repeat(256)).forEach { marker ->
            Assertions.assertThrows(InvalidRequestException::class.java) { provisioningClient.createRepository(sshUrl, marker, "main") }
        }
        Assertions.assertThrows(InvalidRequestException::class.java) { provisioningClient.createRepository(sshUrl, "m-1", " ") }
        assertNothingCreated(name)
    }

    @Test
    fun createWithInvalidZipTest() {
        val name = "create-bad-zip"
        val sshUrl = testService.sshUrl(GROUP, name)
        listOf(
            zipOf(".git/config" to REGULAR),
            zipOf("a/.git/HEAD" to REGULAR),
            zipOf("../outside.txt" to REGULAR),
            "not a zip".toByteArray(),
        ).forEach { zip ->
            Assertions.assertThrows(
                InvalidContentException::class.java,
            ) { provisioningClient.createRepository(sshUrl, "m-1", "main", null, zip) }
        }
        assertNothingCreated(name)
    }

    @Test
    fun createWithUploadOverTheLimitTest() {
        // The limit is `vcs-facade.provisioning.max-content-size` in ft/docker/application-vcs-facade.yml (1MB).
        val name = "create-too-large"
        val sshUrl = testService.sshUrl(GROUP, name)
        val tooLarge = ByteArray(TOO_LARGE_BYTES) { 'a'.code.toByte() }
        val response = httpCreate(sshUrl, ProvisioningTestAuth.withPermission(), mapOf("marker" to "m-1", "branch" to "main"), tooLarge)
        assertRejected(response, 413, "INVALID_CONTENT")
        Assertions.assertThrows(
            InvalidContentException::class.java,
        ) { provisioningClient.createRepository(sshUrl, "m-1", "main", null, tooLarge) }
        assertNothingCreated(name)
    }

    @Test
    fun createWithValidRequestIsNotImplementedTest() {
        val name = "create-valid"
        val sshUrl = testService.sshUrl(GROUP, name)
        val zip = zipOf("run.sh" to EXECUTABLE, "src/a.txt" to REGULAR)
        val response = httpCreate(sshUrl, ProvisioningTestAuth.withPermission(), mapOf("marker" to "m-1", "branch" to "main"), zip)
        Assertions.assertEquals(501, response.statusCode(), response.body())
        val exception = Assertions.assertThrows(VcsFacadeException::class.java) {
            provisioningClient.createRepository(sshUrl, "m-1", "main", "first", zip)
        }
        Assertions.assertEquals(VcsFacadeException::class.java, exception.javaClass)
        assertNothingCreated(name)
    }

    @Test
    fun createOutsideAllowListTest() {
        val sshUrl = testService.sshUrl(FORBIDDEN_GROUP, "repository")
        Assertions.assertThrows(TargetNotAllowedException::class.java) { provisioningClient.createRepository(sshUrl, "m-1", "main") }
        // The allow-list answers before the request is looked at.
        assertRejected(httpCreate(sshUrl, ProvisioningTestAuth.withPermission(), mapOf("marker" to " bad")), 403, "TARGET_NOT_ALLOWED")
    }

    @Test
    fun createWithoutTokenOrPermissionTest() {
        val sshUrl = testService.sshUrl(GROUP, "create-no-access")
        val parts = mapOf("marker" to "m-1", "branch" to "main")
        Assertions.assertEquals(401, httpCreate(sshUrl, null, parts).statusCode())
        Assertions.assertEquals(403, httpCreate(sshUrl, ProvisioningTestAuth.withoutPermission(), parts).statusCode())
        assertNothingCreated("create-no-access")
    }

    private fun assertNothingCreated(name: String) =
        Assertions.assertEquals(RepositoryStatus.MISSING, provisioningClient.getRepositoryState(testService.sshUrl(GROUP, name)).status)

    private fun assertRejected(
        response: HttpResponse<String>,
        status: Int,
        errorCode: String,
    ) {
        Assertions.assertEquals(status, response.statusCode(), response.body())
        Assertions.assertTrue(response.body().contains("\"errorCode\":\"$errorCode\""), response.body())
    }

    /** A zip with one entry per name, the content `content of <name>` and the Unix mode given. */
    private fun zipOf(vararg entries: Pair<String, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipArchiveOutputStream(out).use { zip ->
            entries.forEach { (name, mode) ->
                zip.putArchiveEntry(ZipArchiveEntry(name).apply { unixMode = mode })
                zip.write("content of $name".toByteArray())
                zip.closeArchiveEntry()
            }
        }
        return out.toByteArray()
    }

    /** A raw multipart POST: `sshUrl` in the query, the text parts, and `content` as a file part when given. */
    private fun httpCreate(
        sshUrl: String,
        token: String?,
        parts: Map<String, String>,
        content: ByteArray? = null,
    ): HttpResponse<String> {
        val body = ByteArrayOutputStream()

        fun write(text: String) = body.write(text.toByteArray(StandardCharsets.UTF_8))
        parts.forEach { (name, value) -> write("--$BOUNDARY\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n") }
        content?.let {
            write("--$BOUNDARY\r\nContent-Disposition: form-data; name=\"content\"; filename=\"content.zip\"\r\n")
            write("Content-Type: application/zip\r\n\r\n")
            body.write(it)
            write("\r\n")
        }
        write("--$BOUNDARY--\r\n")
        return HttpClient.newHttpClient().send(
            HttpRequest
                .newBuilder()
                .uri(URI("http://$vcsFacadeHost/rest/api/2/repository?sshUrl=${URLEncoder.encode(sshUrl, StandardCharsets.UTF_8)}"))
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                .also { builder -> token?.let { builder.header("Authorization", "Bearer $it") } }
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }
    // </editor-fold>

    companion object {
        // Both must be in, and the second out of, `vcs-facade.vcs.services[].provisioning.allowed-groups` of the ft config.
        const val ABSENT_GROUP = "absentgroup"
        const val FORBIDDEN_GROUP = "forbidden"

        private const val BOUNDARY = "ft-boundary"
        private const val REGULAR = 0x81A4 // 0100644
        private const val EXECUTABLE = 0x81ED // 0100755

        // Over the 1MB limit, and under what the server swallows of an unread body, so the 413 is answered, not a reset.
        private const val TOO_LARGE_BYTES = 1024 * 1024 + 256 * 1024

        private val PROVISIONING_PATHS = listOf("/rest/api/2/groups", "/rest/api/2/repository/state")

        val vcsFacadeHost = System.getProperty("test.vcs-facade-host")
            ?: throw Exception("System property 'test.vcs-facade-host' must be defined")

        @JvmStatic
        protected val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://$vcsFacadeHost"

            override fun getTimeRetryInMillis() = 180000
        })

        // Declared after `vcsFacadeHost`: the client reads its URL when it is built.
        @JvmStatic
        protected val provisioningClient = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://$vcsFacadeHost"

            override fun getTimeRetryInMillis() = 180000

            override fun getBearerToken() = ProvisioningTestAuth.withPermission()
        })

        val vcsFacadeExternalHost = System.getProperty("test.vcs-facade-external-host")
            ?: throw Exception("System property 'test.vcs-facade-external-host' must be defined")

        val vcsHost: String = System.getProperty("test.vcs-host")
            ?: throw Exception("System property 'test.vcs-host' must be defined")

        val vcsExternalHost = System.getProperty("test.vcs-external-host")
            ?: throw Exception("System property 'test.vcs-external-host' must be defined")
    }
}
