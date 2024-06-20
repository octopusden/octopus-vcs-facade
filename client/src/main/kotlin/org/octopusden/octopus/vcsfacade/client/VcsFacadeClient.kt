package org.octopusden.octopus.vcsfacade.client

import feign.Headers
import feign.Param
import feign.RequestLine
import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

interface VcsFacadeClient {
    @RequestLine("GET rest/api/2/repository/commits?sshUrl={sshUrl}&fromHashOrRef={fromHashOrRef}&fromDate={fromDate}&toHashOrRef={toHashOrRef}")
    fun getCommits(
        @Param("sshUrl") sshUrl: String,
        @Param("fromHashOrRef") fromHashOrRef: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("toHashOrRef") toHashOrRef: String
    ): List<Commit>

    @RequestLine("GET rest/api/2/repository/commits/files?sshUrl={sshUrl}&fromHashOrRef={fromHashOrRef}&fromDate={fromDate}&toHashOrRef={toHashOrRef}&commitFilesLimit={commitFilesLimit}")
    fun getCommitsWithFiles(
        @Param("sshUrl") sshUrl: String,
        @Param("fromHashOrRef") fromHashOrRef: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("toHashOrRef") toHashOrRef: String,
        @Param("commitFilesLimit") commitFilesLimit: Int?
    ): List<CommitWithFiles>

    @RequestLine("GET rest/api/2/repository/commit?sshUrl={sshUrl}&hashOrRef={hashOrRef}")
    fun getCommit(@Param("sshUrl") sshUrl: String, @Param("hashOrRef") hashOrRef: String): Commit

    @RequestLine("GET rest/api/2/repository/commit/files?sshUrl={sshUrl}&hashOrRef={hashOrRef}&commitFilesLimit={commitFilesLimit}")
    fun getCommitWithFiles(
        @Param("sshUrl") sshUrl: String,
        @Param("hashOrRef") hashOrRef: String,
        @Param("commitFilesLimit") commitFilesLimit: Int?
    ): CommitWithFiles

    @RequestLine("GET rest/api/2/repository/issues?sshUrl={sshUrl}&fromHashOrRef={fromHashOrRef}&fromDate={fromDate}&toHashOrRef={toHashOrRef}")
    fun getIssuesFromCommits(
        @Param("sshUrl") sshUrl: String,
        @Param("fromHashOrRef") fromHashOrRef: String?,
        @Param("fromDate", expander = DateToISOExpander::class) fromDate: Date?,
        @Param("toHashOrRef") toHashOrRef: String
    ): List<String>

    @RequestLine("GET rest/api/2/repository/tags?sshUrl={sshUrl}")
    fun getTags(@Param("sshUrl") sshUrl: String): List<Tag>

    @RequestLine("POST rest/api/2/repository/tags?sshUrl={sshUrl}")
    @Headers("Content-Type: application/json")
    fun createTag(@Param("sshUrl") sshUrl: String, createTag: CreateTag): Tag

    @RequestLine("GET rest/api/2/repository/tag?sshUrl={sshUrl}&name={name}")
    fun getTag(@Param("sshUrl") sshUrl: String, @Param("name") name: String): Tag

    @RequestLine("DELETE rest/api/2/repository/tag?sshUrl={sshUrl}&name={name}")
    fun deleteTag(@Param("sshUrl") sshUrl: String, @Param("name") name: String)

    @RequestLine("POST rest/api/2/repository/search-issues-in-ranges")
    @Headers("Content-Type: application/json")
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse

    @RequestLine("POST rest/api/2/repository/pull-requests?sshUrl={sshUrl}")
    @Headers("Content-Type: application/json")
    fun createPullRequest(@Param("sshUrl") sshUrl: String, createPullRequest: CreatePullRequest): PullRequest

    @RequestLine("GET rest/api/2/repository/find/{issueKey}")
    fun findByIssueKey(@Param("issueKey") issueKey: String): SearchSummary

    @RequestLine("GET rest/api/2/repository/find/{issueKey}/branches")
    fun findBranchesByIssueKey(@Param("issueKey") issueKey: String): List<Branch>

    @RequestLine("GET rest/api/2/repository/find/{issueKey}/commits")
    fun findCommitsByIssueKey(@Param("issueKey") issueKey: String): List<Commit>

    @RequestLine("GET rest/api/2/repository/find/{issueKey}/commits/files?commitFilesLimit={commitFilesLimit}")
    fun findCommitsWithFilesByIssueKey(
        @Param("issueKey") issueKey: String,
        @Param("commitFilesLimit") commitFilesLimit: Int?
    ): List<CommitWithFiles>

    @RequestLine("GET rest/api/2/repository/find/{issueKey}/pull-requests")
    fun findPullRequestsByIssueKey(@Param("issueKey") issueKey: String): List<PullRequest>
}
