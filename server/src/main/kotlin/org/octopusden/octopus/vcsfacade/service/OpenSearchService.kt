package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType

interface OpenSearchService {
    fun getRepositories(type: VcsServiceType): Set<Repository>
    fun findRepositoryById(repositoryId: String): Repository?
    fun saveRepository(repository: Repository): Repository
    fun deleteRepository(repository: Repository)
    fun findRefsByRepositoryId(repositoryId: String): Set<Ref>
    fun saveRefs(refs: List<Ref>)
    fun deleteRefsByIds(refsIds: List<String>)
    fun deleteRefsByRepositoryId(repositoryId: String)
    fun findCommitsByRepositoryId(repositoryId: String): Set<Commit>
    fun saveCommits(commits: List<Commit>)
    fun deleteCommitsByIds(commitsIds: List<String>)
    fun deleteCommitsByRepositoryId(repositoryId: String)
    fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequest>
    fun savePullRequests(pullRequests: List<PullRequest>)
    fun deletePullRequestsByIds(pullRequestsIds: List<String>)
    fun deletePullRequestsByRepositoryId(repositoryId: String)
    fun findBranchesByIssueKey(issueKey: String): List<Ref>
    fun findCommitsByIssueKey(issueKey: String): List<Commit>
    fun findPullRequestsByIssueKey(issueKey: String): List<PullRequest>
    fun findByIssueKey(issueKey: String): SearchSummary
}