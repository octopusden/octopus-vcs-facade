package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import java.util.*

abstract class VCSClient(private val vcsProperties: VCSConfig.VCSProperties) {
    private val basePath: String = vcsProperties.host
    protected abstract val repoPrefix: String

    fun isSupport(vcsPath: String): Boolean {
        return vcsPath.toLowerCase().startsWith("$repoPrefix${getHost()}")
    }

    protected fun getHost() = basePath.replace(Regex("^(https|http)://"), "")
    protected fun validateParams(fromId: String?, fromDate: Date?) {
        fromId?.let { _ ->
            fromDate?.let { _ ->
                throw ArgumentsNotCompatibleException("Params 'fromId' and 'fromDate' can not be used together")
            }
        }
    }

    abstract fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit>
    abstract fun getCommits(issueKey: String): List<Commit>
    abstract fun getTags(vcsPath: String): List<Tag>
    abstract fun getCommit(vcsPath: String, commitId: String): Commit
    abstract fun createPullRequest(
        vcsPath: String,
        pullRequestRequest: PullRequestRequest
    ): PullRequestResponse
}
