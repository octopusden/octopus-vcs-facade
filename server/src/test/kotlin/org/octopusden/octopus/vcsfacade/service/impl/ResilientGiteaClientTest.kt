package org.octopusden.octopus.vcsfacade.service.impl

import feign.FeignException
import feign.Request
import feign.codec.DecodeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEntityList
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaUser
import org.octopusden.octopus.infrastructure.gitea.client.getBranchesCommitGraph
import org.octopusden.octopus.infrastructure.gitea.client.getCommit
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import java.nio.charset.StandardCharsets
import java.util.Date

class ResilientGiteaClientTest {
    private lateinit var delegate: GiteaClient
    private lateinit var client: ResilientGiteaClient

    @BeforeEach
    fun setUp() {
        delegate = mock(GiteaClient::class.java)
        client = ResilientGiteaClient(delegate)
    }

    @Test
    fun `getBranchesCommitGraph recovers truncated page and skips files for whole page`() {
        val first = commit("c1")
        val second = commit("c2", parents = listOf("c1"))
        doReturn(GiteaEntityList(false, listOf(GiteaBranch("main", GiteaBranch.PayloadCommit("c2")))))
            .`when`(delegate)
            .getBranches(anyString(), anyString(), anyMap<String, Any>())
        stubCommits(
            pageWithFiles = { throw truncatedResponse() },
            pageWithoutFiles = { GiteaEntityList(false, listOf(second, first)) },
        )

        val commits = client.getBranchesCommitGraph(ORG, REPOSITORY, true).toList()

        assertEquals(listOf("c2", "c1"), commits.map { it.sha })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[0].files)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[1].files)
        assertEquals(second.commit, commits[0].commit)
        assertEquals(second.parents, commits[0].parents)
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `getCommits with date range recovers truncated page`() {
        val first = commit("c1")
        val second = commit("c2", parents = listOf("c1"))
        stubCommits(
            pageWithFiles = { throw truncatedResponse() },
            pageWithoutFiles = { GiteaEntityList(false, listOf(second, first)) },
        )

        val commits = client.getCommits(ORG, REPOSITORY, "c2", null, true)

        assertEquals(listOf("c2", "c1"), commits.map { it.sha })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[0].files)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[1].files)
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `getCommits with hash range recovers truncated page and excludes since commit`() {
        val first = commit("c1")
        val second = commit("c2", parents = listOf("c1"))
        val third = commit("c3", parents = listOf("c2"))
        stubCommits(
            pageWithFiles = { throw truncatedResponse() },
            pageWithoutFiles = { GiteaEntityList(false, listOf(third, second, first)) },
        )
        stubCommitsBySha(mapOf("c1" to first, "c2" to second, "c3" to third), setOf("c1", "c2", "c3"))

        val commits = client.getCommits(ORG, REPOSITORY, "c3", "c1", true)

        assertEquals(setOf("c2", "c3"), commits.map { it.sha }.toSet())
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits.first { it.sha == "c3" }.files)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits.first { it.sha == "c2" }.files)
        verify(delegate, times(2)).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), argThatFiles(true))
    }

    @Test
    fun `getCommit recovers truncated single commit`() {
        val first = commit("c1")
        stubCommitsBySha(mapOf("c1" to first), setOf("c1"))

        val commit = client.getCommit(ORG, REPOSITORY, "c1", true)

        assertEquals("c1", commit.sha)
        assertEquals(first.commit, commit.commit)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commit.files)
    }

    @Test
    fun `getCommit recovers empty response body`() {
        val first = commit("c1")
        doAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(3)
            if (params["files"] == true) {
                throw TestDecodeException()
            } else {
                first.copy(files = null)
            }
        }.`when`(delegate).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())

        val commit = client.getCommit(ORG, REPOSITORY, "c1", true)

        assertEquals("c1", commit.sha)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commit.files)
    }

    @Test
    fun `getCommits recovers empty response body`() {
        val first = commit("c1")
        doAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(2)
            if (params["files"] == true) {
                throw TestDecodeException()
            } else {
                GiteaEntityList(false, listOf(first))
            }
        }.`when`(delegate).getCommits(anyString(), anyString(), anyMap<String, Any>())

        val commits = client.getCommits(ORG, REPOSITORY, mapOf("files" to true, "page" to 1))

        assertEquals(listOf("c1"), commits.values.map { it.sha })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits.values.first().files)
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `failed fallback is propagated`() {
        doAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(3)
            if (params["files"] == true) {
                throw TestDecodeException()
            } else {
                error("fallback failed")
            }
        }.`when`(delegate).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())

        val exception = assertThrows<IllegalStateException> {
            client.getCommit(ORG, REPOSITORY, "c1", true)
        }

        assertEquals("fallback failed", exception.message)
        verify(delegate, times(2)).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `non successful decode exception is propagated`() {
        doAnswer { throw TestDecodeException(status = 500) }
            .`when`(delegate)
            .getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())

        val exception = assertThrows<TestDecodeException> {
            client.getCommit(ORG, REPOSITORY, "c1", true)
        }

        assertEquals(500, exception.status())
        verify(delegate, times(1)).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `missing files parameter is treated as enabled`() {
        val first = commit("c1")
        doAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(2)
            if (params["files"] == false) {
                GiteaEntityList(false, listOf(first))
            } else {
                throw truncatedResponse()
            }
        }.`when`(delegate).getCommits(anyString(), anyString(), anyMap<String, Any>())

        val commits = client.getCommits(ORG, REPOSITORY, mapOf("page" to 1))

        assertEquals(listOf("c1"), commits.values.map { it.sha })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits.values.first().files)
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `non truncated error is propagated`() {
        doAnswer { throw TestFeignException() }
            .`when`(delegate)
            .getCommits(anyString(), anyString(), anyMap<String, Any>())

        val exception = assertThrows<TestFeignException> {
            client.getCommits(ORG, REPOSITORY, mapOf("files" to true, "page" to 1))
        }

        assertEquals("boom", exception.message)
        verify(delegate, times(1)).getCommits(anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `files false is passed through without extra requests`() {
        val first = commit("c1")
        doReturn(GiteaEntityList(false, listOf(first)))
            .`when`(delegate)
            .getCommits(anyString(), anyString(), anyMap<String, Any>())

        val commits = client.getCommits(ORG, REPOSITORY, mapOf("files" to false, "page" to 1))

        assertEquals(listOf("c1"), commits.values.map { it.sha })
        verify(delegate, times(1)).getCommits(anyString(), anyString(), anyMap<String, Any>())
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    @Test
    fun `normal page keeps files`() {
        val first = commit("c1").copy(files = listOf(FILE))
        doReturn(GiteaEntityList(false, listOf(first)))
            .`when`(delegate)
            .getCommits(anyString(), anyString(), anyMap<String, Any>())

        val commits = client.getCommits(ORG, REPOSITORY, mapOf("files" to true, "page" to 1))

        assertEquals(listOf("c1"), commits.values.map { it.sha })
        assertEquals(listOf(FILE), commits.values.first().files)
        verify(delegate, never()).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    private fun stubCommits(
        pageWithFiles: () -> GiteaEntityList<GiteaCommit>,
        pageWithoutFiles: () -> GiteaEntityList<GiteaCommit>,
    ) {
        doAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(2)
            if (params["files"] == true) pageWithFiles() else pageWithoutFiles()
        }.`when`(delegate).getCommits(anyString(), anyString(), anyMap<String, Any>())
    }

    private fun argThatFiles(files: Boolean): Map<String, Any> = argThat { params -> params["files"] == files }

    private fun stubCommitsBySha(
        commits: Map<String, GiteaCommit>,
        commitsWithoutFiles: Set<String>,
    ) {
        doAnswer { invocation ->
            val sha = invocation.getArgument<String>(2)
            val params = invocation.getArgument<Map<String, Any>>(3)
            val commit = commits[sha] ?: error("Unexpected commit '$sha'")
            when {
                params["files"] != true -> commit.copy(files = null)
                sha in commitsWithoutFiles -> throw truncatedResponse()
                else -> commit.copy(files = listOf(FILE))
            }
        }.`when`(delegate).getCommit(anyString(), anyString(), anyString(), anyMap<String, Any>())
    }

    private fun commit(
        sha: String,
        parents: List<String> = emptyList(),
    ) = GiteaCommit(
        sha = sha,
        created = Date(sha.substring(1).toLong() * 1000),
        commit = GiteaCommit.GiteaRepoCommit("message $sha", GiteaCommit.GiteaRepoCommitUser("author")),
        parents = parents.map { GiteaShortCommit(it) },
        author = GiteaUser(1, "user", "avatar"),
        files = null,
    )

    private fun truncatedResponse() = TestFeignException(IllegalStateException("Unexpected end-of-input"))

    private class TestFeignException(
        cause: Throwable? = null,
    ) : FeignException(500, "boom", cause)

    private class TestDecodeException(
        status: Int = 200,
        cause: Throwable = NullPointerException(),
    ) : DecodeException(
            status,
            "decode failed",
            Request.create("GET", "http://localhost", emptyMap(), ByteArray(0), StandardCharsets.UTF_8),
            cause,
        )

    companion object {
        private const val ORG = "org"
        private const val REPOSITORY = "repository"
        private val FILE = GiteaCommit.GiteaCommitAffectedFile(
            "src/Main.java",
            GiteaCommit.GiteaCommitAffectedFileStatus.MODIFIED,
        )
    }
}
