package org.octopusden.octopus.vcsfacade

import java.util.Date
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

abstract class BaseVcsFacadeFunctionalTest(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {
    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest) =
        client.createPullRequest(sshUrl, createPullRequest)

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = client.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitFilesLimit: Int?
    ) = client.getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef, commitFilesLimit)

    override fun getCommit(sshUrl: String, hashOrRef: String) = client.getCommit(sshUrl, hashOrRef)

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String, commitFilesLimit: Int?) =
        client.getCommitWithFiles(sshUrl, hashOrRef, commitFilesLimit)

    override fun getIssuesFromCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = client.getIssuesFromCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getTags(sshUrl: String) = client.getTags(sshUrl)

    override fun createTag(sshUrl: String, createTag: CreateTag) = client.createTag(sshUrl, createTag)

    override fun getTag(sshUrl: String, name: String) = client.getTag(sshUrl, name)

    override fun deleteTag(sshUrl: String, name: String) = client.deleteTag(sshUrl, name)

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) =
        client.searchIssuesInRanges(searchRequest)

    override fun findByIssueKey(issueKey: String) = client.findByIssueKey(issueKey)

    override fun findBranchesByIssueKey(issueKey: String) = client.findBranchesByIssueKey(issueKey)

    override fun findCommitsByIssueKey(issueKey: String) = client.findCommitsByIssueKey(issueKey)

    override fun findCommitsWithFilesByIssueKey(issueKey: String, commitFilesLimit: Int?) =
        client.findCommitsWithFilesByIssueKey(issueKey, commitFilesLimit)

    override fun findPullRequestsByIssueKey(issueKey: String) = client.findPullRequestsByIssueKey(issueKey)

    companion object {
        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = Configuration.model.vcsFacadeUrl
            override fun getTimeRetryInMillis() = 180000
        })
    }
}
