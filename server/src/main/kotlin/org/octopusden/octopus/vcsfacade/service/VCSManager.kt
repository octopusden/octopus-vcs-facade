package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary

interface VCSManager {
    fun getTags(vcsPath: String): List<Tag>
    fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit>
    fun getCommit(vcsPath: String, commitIdOrRef: String): Commit
    fun createPullRequest(vcsPath: String, createPullRequest: CreatePullRequest): PullRequest
    fun getIssueRanges(searchRequest: SearchIssuesInRangesRequest): Map<String, Set<RepositoryRange>>
    fun findBranches(issueKey: String): List<Branch>
    fun findCommits(issueKey: String): List<Commit>
    fun findPullRequests(issueKey: String): List<PullRequest>
    fun find(issueKey: String): SearchSummary
}
