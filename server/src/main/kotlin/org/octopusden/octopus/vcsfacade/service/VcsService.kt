package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsConfig
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate

abstract class VcsService(vcsServiceProperties: VcsConfig.VcsServiceProperties) {
    val type = vcsServiceProperties.type
    protected val httpUrl = vcsServiceProperties.host.lowercase().trimEnd('/')
    val host = httpUrl.replace("^(https|http)://".toRegex(), "")

    abstract fun getRepositories(): Sequence<Repository>
    abstract fun findRepository(group: String, repository: String): Repository?
    abstract fun getBranches(group: String, repository: String): Sequence<Branch>
    abstract fun getTags(group: String, repository: String): Sequence<Tag>
    abstract fun createTag(group: String, repository: String, createTag: CreateTag): Tag
    abstract fun getTag(group: String, repository: String, name: String): Tag
    abstract fun deleteTag(group: String, repository: String, name: String)
    abstract fun getCommits(group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String): Sequence<Commit>
    abstract fun getCommitsWithFiles(group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String): Sequence<CommitWithFiles>
    abstract fun getBranchesCommitGraph(group: String, repository: String): Sequence<CommitWithFiles>
    abstract fun getCommit(group: String, repository: String, hashOrRef: String): Commit
    abstract fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles
    abstract fun getPullRequests(group: String, repository: String): Sequence<PullRequest>
    abstract fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest
    abstract fun getPullRequest(group: String, repository: String, index: Long): PullRequest
    abstract fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit>
    abstract fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest>
    abstract fun findBranches(issueKey: String): Sequence<Branch>
    abstract fun findCommits(issueKey: String): Sequence<Commit>
    abstract fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles>
    abstract fun findPullRequests(issueKey: String): Sequence<PullRequest>

    companion object {
        private val sshUrlRegex = "(?:ssh://)?git@([^:/]+(?::\\d+)?)[:/]((?:[^/]+/)+)([^/]+).git".toRegex()

        fun parseSshUrl(sshUrl: String) = sshUrlRegex.find(sshUrl.lowercase())?.destructured?.let {
            Triple(it.component1(), it.component2().trimEnd('/'), it.component3())
        } ?: throw IllegalArgumentException("$sshUrl is not valid repository SSH URL")
    }
}
