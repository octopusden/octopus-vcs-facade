package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository

interface OpenSearchService {
    fun findRepositoryById(repositoryId: String): Repository?
    fun saveRepository(repository: Repository): Repository
    fun findRefsByRepositoryId(repositoryId: String): List<Ref>
    fun saveRefs(refs: List<Ref>)
    fun deleteRefsByIds(refsIds: List<String>)
    fun findCommitsByRepositoryId(repositoryId: String): List<Commit>
    fun saveCommits(commits: List<Commit>)
    fun deleteCommitsByIds(commitsIds: List<String>)
    fun findPullRequestsByRepositoryId(repositoryId: String): List<PullRequest>
    fun savePullRequests(pullRequests: List<PullRequest>)
    fun deletePullRequestsByIds(pullRequestsIds: List<String>)
    fun findBranchesByIssueKey(issueKey: String): Map<Repository, List<Ref>>
    fun findCommitsByIssueKey(issueKey: String): Map<Repository, List<Commit>>
    fun findPullRequestsByIssueKey(issueKey: String): Map<Repository, List<PullRequest>>
    fun findByIssueKey(issueKey: String): SearchSummary
}