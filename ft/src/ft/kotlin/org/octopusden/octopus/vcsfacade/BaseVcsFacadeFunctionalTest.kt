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
) : BaseVcsFacadeTestExtended(testService, testClient) {
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

    override fun getTags(sshUrl: String, names: Set<String>?) = client.getTags(sshUrl, names)

    override fun createTag(sshUrl: String, createTag: CreateTag) = client.createTag(sshUrl, createTag)

    override fun getTag(sshUrl: String, name: String) = client.getTag(sshUrl, name)

    override fun deleteTag(sshUrl: String, name: String) = client.deleteTag(sshUrl, name)

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) =
        client.searchIssuesInRanges(searchRequest)

    override fun findByIssueKeys(issueKeys: Set<String>) = client.findByIssueKeys(issueKeys)

    override fun findBranchesByIssueKeys(issueKeys: Set<String>) = client.findBranchesByIssueKeys(issueKeys)

    override fun findCommitsByIssueKeys(issueKeys: Set<String>) = client.findCommitsByIssueKeys(issueKeys)

    override fun findCommitsWithFilesByIssueKeys(issueKeys: Set<String>, commitFilesLimit: Int?) =
        client.findCommitsWithFilesByIssueKeys(issueKeys, commitFilesLimit)

    override fun findPullRequestsByIssueKeys(issueKeys: Set<String>) = client.findPullRequestsByIssueKeys(issueKeys)

    companion object {
        val vcsFacadeHost = System.getProperty("test.vcs-facade-host")
            ?: throw Exception("System property 'test.vcs-facade-host' must be defined")

        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://$vcsFacadeHost"
            override fun getTimeRetryInMillis() = 180000
        })

        val vcsFacadeExternalHost = System.getProperty("test.vcs-facade-external-host")
            ?: throw Exception("System property 'test.vcs-facade-external-host' must be defined")

        val vcsHost: String = System.getProperty("test.vcs-host")
            ?: throw Exception("System property 'test.vcs-host' must be defined")

        val vcsExternalHost = System.getProperty("test.vcs-external-host")
            ?: throw Exception("System property 'test.vcs-external-host' must be defined")
    }
}
