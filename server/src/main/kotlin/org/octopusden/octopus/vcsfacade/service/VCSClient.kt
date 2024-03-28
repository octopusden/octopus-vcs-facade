package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType

abstract class VCSClient(vcsProperties: VCSConfig.VCSProperties, val vcsServiceType: VcsServiceType) {
    protected val httpUrl = vcsProperties.host.lowercase().trimEnd('/')
    protected val host = httpUrl.replace("^(https|http)://".toRegex(), "")

    protected abstract val sshUrlRegex: Regex
    fun isSupport(sshUrl: String) = sshUrlRegex.matches(sshUrl.lowercase())
    fun parse(sshUrl: String) =
        sshUrlRegex.find(sshUrl.lowercase())!!.destructured.let { it.component1().trimEnd('/') to it.component2() }

    abstract fun getSshUrl(group: String, repository: String): String
    abstract fun getBranches(group: String, repository: String): List<Branch>
    abstract fun getTags(group: String, repository: String): List<Tag>
    abstract fun getCommits(group: String, repository: String, toId: String, fromId: String): Collection<Commit>
    abstract fun getCommits(group: String, repository: String, toId: String, fromDate: Date?): Collection<Commit>
    abstract fun getCommit(group: String, repository: String, id: String): Commit
    abstract fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest
    abstract fun getPullRequest(group: String, repository: String, index: Long): PullRequest
    fun findBranches(group: String, repository: String, names: Set<String>) = getBranches(group, repository).filter { it.name in names }
    abstract fun findCommits(group: String, repository: String, ids: Set<String>): List<Commit>
    abstract fun findPullRequests(group: String, repository: String, indexes: Set<Long>): List<PullRequest>
    abstract fun findBranches(issueKey: String): List<Branch>
    abstract fun findCommits(issueKey: String): List<Commit>
    abstract fun findPullRequests(issueKey: String): List<PullRequest>
}
