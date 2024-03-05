package org.octopusden.octopus.vcsfacade.client

import feign.Headers
import feign.Param
import feign.RequestLine
import java.util.*
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException

interface VcsFacadeClient {
    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/commits?vcsPath={vcsPath}&from={from}&fromDate={fromDate}&to={to}")
    fun getCommits(
        @Param("vcsPath") vcsPath: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<Commit>

    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/commit?vcsPath={vcsPath}&commitId={commitIdOrRef}")
    fun getCommit(@Param("vcsPath") vcsPath: String, @Param("commitIdOrRef") commitIdOrRef: String): Commit

    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/issues?vcsPath={vcsPath}&from={from}&fromDate={fromDate}&to={to}")
    fun getIssuesFromCommits(
        @Param("vcsPath") vcsPath: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<String>

    @Throws(IllegalStateException::class)
    @RequestLine("GET repository/tags?vcsPath={vcsPath}")
    fun getTags(@Param("vcsPath") vcsUrl: String): List<Tag>

    @RequestLine("POST repository/search-issues-in-ranges")
    @Headers("Content-Type: application/json")
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse

    @RequestLine("POST repository/pull-requests?vcsPath={vcsPath}")
    @Headers("Content-Type: application/json")
    fun createPullRequest(@Param("vcsPath") vcsPath: String, createPullRequest: CreatePullRequest): PullRequest

    @RequestLine("GET repository/find/{issueKey}")
    fun findByIssueKey(@Param("issueKey") issueKey: String): SearchSummary

    @RequestLine("GET repository/find/{issueKey}/branches")
    fun findBranchesByIssueKey(@Param("issueKey") issueKey: String): List<Branch>

    @RequestLine("GET repository/find/{issueKey}/commits")
    fun findCommitsByIssueKey(@Param("issueKey") issueKey: String): List<Commit>

    @RequestLine("GET repository/find/{issueKey}/pull-requests")
    fun findPullRequestsByIssueKey(@Param("issueKey") issueKey: String): List<PullRequest>
}
