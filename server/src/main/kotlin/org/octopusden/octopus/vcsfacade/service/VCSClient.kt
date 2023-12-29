package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VCSConfig

abstract class VCSClient(vcsProperties: VCSConfig.VCSProperties) {
    protected val host = vcsProperties.host.lowercase()
        .replace("^(https|http)://".toRegex(), "").trimEnd('/', ':')

    protected abstract val vcsPathRegex: Regex
    fun isSupport(vcsPath: String) = vcsPathRegex.matches(vcsPath.lowercase())

    abstract fun getCommits(vcsPath: String, toId: String, fromId: String): Collection<Commit>
    abstract fun getCommits(vcsPath: String, toId: String, fromDate: Date?): Collection<Commit>
    abstract fun getCommits(issueKey: String): List<Commit>
    abstract fun getTags(vcsPath: String): List<Tag>
    abstract fun getCommit(vcsPath: String, commitIdOrRef: String): Commit
    abstract fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse
}
