package org.octopusden.octopus.vcsfacade

import java.util.Date
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

abstract class BaseVcsFacadeFunctionalTest(testClient: TestClient, sshUrlFormat: String) :
    BaseVcsFacadeTest(testClient, sshUrlFormat) {

    override fun requestTags(
        sshUrl: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val tags = client.getTags(sshUrl)
            checkSuccess(tags)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        }
    }

    override fun requestCommitsInterval(
        sshUrl: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commits = client.getCommits(sshUrl, fromId, fromDate, toId)
            checkSuccess(commits)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        } catch (e: ArgumentsNotCompatibleException) {
            checkError(Pair(400, e.message!!))
        }
    }

    override fun requestCommitsByIssueKey(
        issueKey: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commits = client.findCommitsByIssueKey(issueKey)
            checkSuccess(commits)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        }
    }

    override fun requestCommitById(
        sshUrl: String,
        commitId: String,
        status: Int,
        checkSuccess: (Commit) -> Unit,
        checkError: CheckError
    ) {
        try {
            val commit = client.getCommit(sshUrl, commitId)
            checkSuccess(commit)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        }
    }

    override fun searchIssuesInRanges(
        searchRequest: SearchIssuesInRangesRequest,
        status: Int,
        checkSuccess: (SearchIssueInRangesResponse) -> Unit,
        checkError: CheckError
    ) {
        try {
            val response = client.searchIssuesInRanges(searchRequest)
            checkSuccess(response)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        }
    }

    override fun createPullRequest(
        sshUrl: String,
        createPullRequest: CreatePullRequest,
        status: Int,
        checkSuccess: (PullRequest) -> Unit,
        checkError: CheckError
    ) {
        try {
            val pullRequest = client.createPullRequest(sshUrl, createPullRequest)
            checkSuccess(pullRequest)
        } catch (e: NotFoundException) {
            checkError(Pair(400, e.message!!))
        }
    }

    companion object {
        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://localhost:8080"
            override fun getTimeRetryInMillis() = 180000
        })
    }
}
