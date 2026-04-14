package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType

class VcsServiceSshUrlParsingTest {

    private fun createService(sshUrl: String, type: VcsServiceType): VcsService {
        val properties = VcsProperties.Service(
            id = "test",
            type = type,
            httpUrl = "http://localhost",
            sshUrl = sshUrl,
            token = "test-token",
            username = null,
            password = null,
            healthCheck = null
        )
        return StubVcsService(properties)
    }

    private val bitbucketService = createService("ssh://git@bitbucket.example.com", VcsServiceType.BITBUCKET)
    private val giteaService = createService("ssh://git@gitea.example.com", VcsServiceType.GITEA)

    @Test
    fun `bitbucket parse standard SSH URL`() {
        val (group, repository) = bitbucketService.parse("ssh://git@bitbucket.example.com/project/repo.git")
        assertEquals("project", group)
        assertEquals("repo", repository)
    }

    @Test
    fun `bitbucket parse SSH URL with scm prefix`() {
        val (group, repository) = bitbucketService.parse("ssh://git@bitbucket.example.com/scm/project/repo.git")
        assertEquals("project", group)
        assertEquals("repo", repository)
    }

    @Test
    fun `bitbucket isSupported for standard URL`() {
        assertTrue(bitbucketService.isSupported("ssh://git@bitbucket.example.com/project/repo.git"))
    }

    @Test
    fun `bitbucket isSupported for URL with scm prefix`() {
        assertTrue(bitbucketService.isSupported("ssh://git@bitbucket.example.com/scm/project/repo.git"))
    }

    @Test
    fun `bitbucket isSupported returns false for different host`() {
        assertFalse(bitbucketService.isSupported("ssh://git@other-host.com/project/repo.git"))
    }

    @Test
    fun `gitea parse standard SSH URL`() {
        val (group, repository) = giteaService.parse("ssh://git@gitea.example.com/org/repo.git")
        assertEquals("org", group)
        assertEquals("repo", repository)
    }

    @Test
    fun `gitea parse SSH URL with org named scm`() {
        val (group, repository) = giteaService.parse("ssh://git@gitea.example.com/scm/repo.git")
        assertEquals("scm", group)
        assertEquals("repo", repository)
    }

    @Test
    fun `gitea isSupported for standard URL`() {
        assertTrue(giteaService.isSupported("ssh://git@gitea.example.com/org/repo.git"))
    }

    @Test
    fun `gitea isSupported returns false for different host`() {
        assertFalse(giteaService.isSupported("ssh://git@bitbucket.example.com/project/repo.git"))
    }

    @Test
    fun `gitea parse SCP-style URL with colon`() {
        val (group, repository) = giteaService.parse("ssh://git@gitea.example.com:org/repo.git")
        assertEquals("org", group)
        assertEquals("repo", repository)
    }

    @Test
    fun `isSupported does not match host with dots as wildcards`() {
        assertFalse(bitbucketService.isSupported("ssh://git@bitbucketXexampleYcom/project/repo.git"))
    }

    private class StubVcsService(properties: VcsProperties.Service) : VcsService(properties) {
        override fun getRepositories(): Sequence<Repository> = TODO()
        override fun findRepository(group: String, repository: String): Repository? = TODO()
        override fun getBranches(group: String, repository: String): Sequence<Branch> = TODO()
        override fun getTags(group: String, repository: String): Sequence<Tag> = TODO()
        override fun createTag(group: String, repository: String, createTag: CreateTag): Tag = TODO()
        override fun getTag(group: String, repository: String, name: String): Tag = TODO()
        override fun deleteTag(group: String, repository: String, name: String) = TODO()
        override fun getCommits(group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String): Sequence<Commit> = TODO()
        override fun getCommitsWithFiles(group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String): Sequence<CommitWithFiles> = TODO()
        override fun getBranchesCommitGraph(group: String, repository: String): Sequence<CommitWithFiles> = TODO()
        override fun getCommit(group: String, repository: String, hashOrRef: String): Commit = TODO()
        override fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles = TODO()
        override fun getPullRequests(group: String, repository: String): Sequence<PullRequest> = TODO()
        override fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest = TODO()
        override fun getPullRequest(group: String, repository: String, index: Long): PullRequest = TODO()
        override fun findTags(group: String, repository: String, names: Set<String>): Sequence<Tag> = TODO()
        override fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit> = TODO()
        override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest> = TODO()
        override fun findBranches(issueKey: String): Sequence<Branch> = TODO()
        override fun findCommits(issueKey: String): Sequence<Commit> = TODO()
        override fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles> = TODO()
        override fun findPullRequests(issueKey: String): Sequence<PullRequest> = TODO()
    }
}
