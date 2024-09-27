package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate

abstract class VcsService(vcsInstanceProperties: VcsProperties.VcsInstanceProperties) {
    protected val httpUrl = vcsInstanceProperties.host.lowercase().trimEnd('/')
    protected val host = httpUrl.replace("^(https|http)://".toRegex(), "")

    protected abstract val sshUrlRegex: Regex
    fun isSupport(sshUrl: String) = sshUrlRegex.matches(sshUrl.lowercase())
    fun parse(sshUrl: String) =
        sshUrlRegex.find(sshUrl.lowercase())!!.destructured.let { it.component1().trimEnd('/') to it.component2() }

    abstract fun getBranches(group: String, repository: String): Sequence<Branch>
    abstract fun getTags(group: String, repository: String): Sequence<Tag>
    abstract fun createTag(group: String, repository: String, createTag: CreateTag): Tag
    abstract fun getTag(group: String, repository: String, name: String): Tag
    abstract fun deleteTag(group: String, repository: String, name: String)
    abstract fun getCommits(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<Commit>

    abstract fun getCommitsWithFiles(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<CommitWithFiles>

    abstract fun getCommit(group: String, repository: String, hashOrRef: String): Commit
    abstract fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles
    abstract fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest
    abstract fun getPullRequest(group: String, repository: String, index: Long): PullRequest
    abstract fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit>
    abstract fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest>
    abstract fun findBranches(issueKey: String): Sequence<Branch>
    abstract fun findCommits(issueKey: String): Sequence<Commit>
    abstract fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles>
    abstract fun findPullRequests(issueKey: String): Sequence<PullRequest>
}
