package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

interface VcsManager {
    fun getTags(sshUrl: String): List<Tag>
    fun getCommits(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String): List<Commit>
    fun getCommitsWithFiles(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String): List<CommitWithFiles>
    fun getCommit(sshUrl: String, hashOrRef: String): Commit
    fun getCommitWithFiles(sshUrl: String, hashOrRef: String): CommitWithFiles
    fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse
    fun findBranches(issueKey: String): List<Branch>
    fun findCommits(issueKey: String): List<Commit>
    fun findCommitsWithFiles(issueKey: String): List<CommitWithFiles>
    fun findPullRequests(issueKey: String): List<PullRequest>
    fun find(issueKey: String): SearchSummary
}
