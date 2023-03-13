package org.octopusden.vcsfacade

import org.octopusden.vcsfacade.client.common.dto.Commit
import org.octopusden.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.vcsfacade.client.common.dto.Tag
import org.octopusden.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.vcsfacade.client.impl.VcsFacadeClientParametersProvider
import java.util.*

class VcsFacadeTest : BaseVcsFacadeTest() {

    override val gitlabHost: String
        get() = "mockserver:1080"

    override val bitbucketHost: String
        get() = "bitbucket:7990"

    override fun requestTags(
        repository: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val tags = client.getTags(repository)
            checkSuccess(tags)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: IllegalStateException) {
            checkError(Pair(500, e.message!!))
        }
    }

    override fun requestCommitsInterval(
        repository: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commits = client.getCommits(repository, fromId, fromDate, toId)
            checkSuccess(commits)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: ArgumentsNotCompatibleException) {
            checkError(Pair(400, e.message!!))
        } catch (e: IllegalStateException) {
            checkError(Pair(500, e.message!!))
        }
    }

    override fun requestCommitsByIssueKey(
        issueKey: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commits = client.getCommits(issueKey)
            checkSuccess(commits)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: IllegalStateException) {
            checkError(Pair(500, e.message!!))
        }
    }

    override fun requestCommitById(
        vcsPath: String,
        commitId: String,
        status: Int,
        checkSuccess: (Commit) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commit = client.getCommit(vcsPath, commitId)
            checkSuccess(commit)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: IllegalStateException) {
            checkError(Pair(500, e.message!!))
        }
    }

    override fun searchIssuesInRanges(
        searchRequest: SearchIssuesInRangesRequest,
        status: Int,
        checkSuccess: (SearchIssueInRangesResponse) -> Unit,
        checkError: CheckError
    ) {
    }

    override fun createPullRequest(
        repository: String,
        pullRequestRequest: PullRequestRequest,
        status: Int,
        checkSuccess: (PullRequestResponse) -> Unit,
        checkError: CheckError
    ) {
        try {
            val pullRequest = client.createPullRequest(repository, pullRequestRequest)
            checkSuccess(pullRequest)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: IllegalStateException) {
            checkError(Pair(500, e.message!!))
        }
    }

    companion object {
        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://localhost:8080"
            override fun getTimeRetryInMillis() = 180000
        })
    }
}
