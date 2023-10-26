package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import java.util.Locale
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VCSConfig

abstract class VCSClient(vcsProperties: VCSConfig.VCSProperties) {
    private val basePath: String = vcsProperties.host
    protected abstract val repoPrefix: String

    fun isSupport(vcsPath: String): Boolean {
        return vcsPath.lowercase(Locale.getDefault()).startsWith("$repoPrefix${getHost()}")
    }

    protected fun getHost() = basePath.replace(Regex("^(https|http)://"), "")

    abstract fun getCommits(vcsPath: String, toId: String, fromId: String): Collection<Commit>
    abstract fun getCommits(vcsPath: String, toId: String, fromDate: Date?): Collection<Commit>
    abstract fun getCommits(issueKey: String): List<Commit>
    abstract fun getTags(vcsPath: String): List<Tag>
    abstract fun getCommit(vcsPath: String, commitIdOrRef: String): Commit
    abstract fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse
}
