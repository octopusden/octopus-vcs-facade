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
    protected val url = vcsProperties.host.lowercase().trimEnd('/')
    protected val host = url.replace("^(https|http)://".toRegex(), "")

    protected abstract val vcsPathRegex: Regex
    fun isSupport(vcsPath: String) = vcsPathRegex.matches(vcsPath.lowercase())
    fun parseRepository(vcsPath: String) = vcsPathRegex.find(vcsPath.lowercase())!!.destructured.let { it.component1().trimEnd('/') to it.component2() }

    abstract fun getBranches(group: String, repository: String): List<Branch>
    abstract fun getTags(group: String, repository: String): List<Tag>
    abstract fun getCommits(group: String, repository: String, toId: String, fromId: String): Collection<Commit>
    abstract fun getCommits(group: String, repository: String, toId: String, fromDate: Date?): Collection<Commit>
    abstract fun getCommit(group: String, repository: String, commitIdOrRef: String): Commit
    abstract fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest): PullRequest
    abstract fun getPullRequest(group: String, repository: String, index: Long): PullRequest
    abstract fun findBranches(issueKey: String): List<Branch>
    abstract fun findCommits(issueKey: String): List<Commit>
    abstract fun findPullRequests(issueKey: String): List<PullRequest>
}
