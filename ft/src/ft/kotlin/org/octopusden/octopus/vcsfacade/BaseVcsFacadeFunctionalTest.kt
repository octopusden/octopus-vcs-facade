package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider
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

    companion object {
        // Both must be in, and the second out of, `vcs-facade.vcs.services[].provisioning.allowed-groups` of the ft config.
        const val ABSENT_GROUP = "absentgroup"
        const val FORBIDDEN_GROUP = "forbidden"

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
