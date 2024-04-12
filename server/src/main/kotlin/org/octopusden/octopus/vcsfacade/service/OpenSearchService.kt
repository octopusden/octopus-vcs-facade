package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType


interface OpenSearchService {
    fun getRepositories(type: VcsServiceType): Set<RepositoryDocument>
    fun findRepositoryById(repositoryId: String): RepositoryDocument?
    fun saveRepository(repository: RepositoryDocument): RepositoryDocument
    fun deleteRepository(repository: RepositoryDocument)
    fun findRefsByRepositoryId(repositoryId: String): Set<RefDocument>
    fun saveRefs(refs: List<RefDocument>)
    fun deleteRefsByIds(refsIds: List<String>)
    fun deleteRefsByRepositoryId(repositoryId: String)
    fun findCommitsByRepositoryId(repositoryId: String): Set<CommitDocument>
    fun saveCommits(commits: List<CommitDocument>)
    fun deleteCommitsByIds(commitsIds: List<String>)
    fun deleteCommitsByRepositoryId(repositoryId: String)
    fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequestDocument>
    fun savePullRequests(pullRequests: List<PullRequestDocument>)
    fun deletePullRequestsByIds(pullRequestsIds: List<String>)
    fun deletePullRequestsByRepositoryId(repositoryId: String)
    fun findBranchesByIssueKey(issueKey: String): List<Branch>
    fun findCommitsByIssueKey(issueKey: String): List<Commit>
    fun findPullRequestsByIssueKey(issueKey: String): List<PullRequest>
    fun findByIssueKey(issueKey: String): SearchSummary
}