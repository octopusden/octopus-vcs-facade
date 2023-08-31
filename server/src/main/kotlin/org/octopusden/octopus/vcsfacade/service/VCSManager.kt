package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import java.util.*

interface VCSManager {
    fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit>
    fun getTagsForRepository(vcsPath: String): List<Tag>
    fun findCommits(issueKey: String): List<Commit>
    fun findCommit(vcsPath: String, commitId: String): Commit
    fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse
    fun getIssueRanges(searchRequest: SearchIssuesInRangesRequest): Map<String, Set<RepositoryRange>>
}
