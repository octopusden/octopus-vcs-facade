package org.octopusden.octopus.vcsfacade.client

import feign.Headers
import feign.Param
import feign.RequestLine
import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

interface VcsFacadeClient {
    @RequestLine("GET rest/api/1/repository/commits?sshUrl={sshUrl}&from={from}&fromDate={fromDate}&to={to}")
    fun getCommits(
        @Param("sshUrl") sshUrl: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<Commit>

    @RequestLine("GET rest/api/1/repository/commit?sshUrl={sshUrl}&commitId={commitId}")
    fun getCommit(@Param("sshUrl") sshUrl: String, @Param("commitId") commitId: String): Commit

    @RequestLine("GET rest/api/1/repository/issues?sshUrl={sshUrl}&from={from}&fromDate={fromDate}&to={to}")
    fun getIssuesFromCommits(
        @Param("sshUrl") sshUrl: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<String>

    @RequestLine("GET rest/api/1/repository/tags?sshUrl={sshUrl}")
    fun getTags(@Param("sshUrl") sshUrl: String): List<Tag>

    @RequestLine("POST rest/api/1/repository/search-issues-in-ranges")
    @Headers("Content-Type: application/json")
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse

    @RequestLine("POST rest/api/1/repository/pull-requests?sshUrl={sshUrl}")
    @Headers("Content-Type: application/json")
    fun createPullRequest(@Param("sshUrl") sshUrl: String, createPullRequest: CreatePullRequest): PullRequest

    @RequestLine("GET rest/api/1/repository/find/{issueKey}")
    fun findByIssueKey(@Param("issueKey") issueKey: String): SearchSummary

    @RequestLine("GET rest/api/1/repository/find/{issueKey}/branches")
    fun findBranchesByIssueKey(@Param("issueKey") issueKey: String): List<Branch>

    @RequestLine("GET rest/api/1/repository/find/{issueKey}/commits")
    fun findCommitsByIssueKey(@Param("issueKey") issueKey: String): List<Commit>

    @RequestLine("GET rest/api/1/repository/find/{issueKey}/pull-requests")
    fun findPullRequestsByIssueKey(@Param("issueKey") issueKey: String): List<PullRequest>
}
