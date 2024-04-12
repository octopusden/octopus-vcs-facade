package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

interface VCSManager {
    fun getTags(sshUrl: String): List<Tag>
    fun getCommits(sshUrl: String, fromId: String?, fromDate: Date?, toId: String): List<Commit>
    fun getCommit(sshUrl: String, id: String): Commit
    fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse
    fun findBranches(issueKey: String): List<Branch>
    fun findCommits(issueKey: String): List<Commit>
    fun findPullRequests(issueKey: String): List<PullRequest>
    fun find(issueKey: String): SearchSummary
}
