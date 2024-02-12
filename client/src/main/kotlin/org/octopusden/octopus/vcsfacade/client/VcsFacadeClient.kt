package org.octopusden.octopus.vcsfacade.client

import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import feign.Headers
import feign.Param
import feign.RequestLine
import java.util.*

interface VcsFacadeClient {

    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/commits?vcsPath={vcsPath}&from={from}&fromDate={fromDate}&to={to}")
    fun getCommits(
        @Param("vcsPath") vcsPath: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<Commit>

    @RequestLine("GET repository/issues/{issueKey}")
    fun getCommits(@Param("issueKey") issueKey: String): List<Commit>

    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/commit?vcsPath={vcsPath}&commitId={cid}")
    fun getCommit(@Param("vcsPath") vcsPath: String, @Param("cid") cid: String): Commit

    @Throws(IllegalStateException::class)
    @RequestLine("GET repository/tags?vcsPath={vcsPath}")
    fun getTags(@Param("vcsPath") vcsUrl: String): List<Tag>

    @Throws(NotFoundException::class, IllegalStateException::class)
    @RequestLine("GET repository/issues?vcsPath={vcsPath}&from={from}&fromDate={fromDate}&to={to}")
    fun getIssuesFromCommits(
        @Param("vcsPath") vcsPath: String,
        @Param("from") fromId: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("to") toId: String
    ): List<String>

    @RequestLine("POST repository/search-issues-in-ranges")
    @Headers("Content-Type: application/json")
    fun analyzeRepositoryGraph(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse

    @RequestLine("POST repository/pull-requests?vcsPath={vcsPath}")
    @Headers("Content-Type: application/json")
    fun createPullRequest(@Param("vcsPath") vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse
}
