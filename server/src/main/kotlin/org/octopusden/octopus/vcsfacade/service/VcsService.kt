package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsConfig

abstract class VcsService(vcsProperties: VcsConfig.VcsProperties) {
    protected val httpUrl = vcsProperties.host.lowercase().trimEnd('/')
    protected val host = httpUrl.replace("^(https|http)://".toRegex(), "")

    protected abstract val sshUrlRegex: Regex
    fun isSupport(sshUrl: String) = sshUrlRegex.matches(sshUrl.lowercase())
    fun parse(sshUrl: String) =
        sshUrlRegex.find(sshUrl.lowercase())!!.destructured.let { it.component1().trimEnd('/') to it.component2() }

    abstract fun getSshUrl(group: String, repository: String): String
    abstract fun getBranches(group: String, repository: String): List<Branch>
    abstract fun getTags(group: String, repository: String): List<Tag>
    abstract fun getCommits(group: String, repository: String, fromHashOrRef: String, toHashOrRef: String): List<Commit>
    abstract fun getCommits(group: String, repository: String, fromDate: Date?, toHashOrRef: String): List<Commit>
    abstract fun getCommit(group: String, repository: String, hashOrRef: String): Commit
    abstract fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest
    abstract fun getPullRequest(group: String, repository: String, index: Long): PullRequest
    abstract fun findCommits(group: String, repository: String, hashes: Set<String>): List<Commit>
    abstract fun findPullRequests(group: String, repository: String, indexes: Set<Long>): List<PullRequest>
    abstract fun findBranches(issueKey: String): List<Branch>
    abstract fun findCommits(issueKey: String): List<Commit>
    abstract fun findPullRequests(issueKey: String): List<PullRequest>
}
