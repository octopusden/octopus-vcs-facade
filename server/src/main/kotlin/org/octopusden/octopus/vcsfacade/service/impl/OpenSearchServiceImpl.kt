package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.document.RepositoryLink
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.repository.CommitRepository
import org.octopusden.octopus.vcsfacade.repository.PullRequestRepository
import org.octopusden.octopus.vcsfacade.repository.RefRepository
import org.octopusden.octopus.vcsfacade.repository.RepositoryRepository
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class OpenSearchServiceImpl(
    private val repositoryRepository: RepositoryRepository,
    private val refRepository: RefRepository,
    private val commitRepository: CommitRepository,
    private val pullRequestRepository: PullRequestRepository
) : OpenSearchService {
    override fun findRepositoryById(repositoryId: String) =
        repositoryRepository.findById(repositoryId).getOrNull()

    override fun saveRepository(repository: Repository) =
        repositoryRepository.save(repository)

    override fun findRefsByRepositoryId(repositoryId: String) =
        refRepository.findAllByRepositoryId(repositoryId)

    override fun saveRefs(refs: List<Ref>) {
        refRepository.saveAll(refs)
    }

    override fun deleteRefsByIds(refsIds: List<String>) =
        refRepository.deleteAllById(refsIds)

    override fun findCommitsByRepositoryId(repositoryId: String) =
        commitRepository.findAllByRepositoryId(repositoryId)

    override fun saveCommits(commits: List<Commit>) {
        commitRepository.saveAll(commits)
    }

    override fun deleteCommitsByIds(commitsIds: List<String>) =
        commitRepository.deleteAllById(commitsIds)

    override fun findPullRequestsByRepositoryId(repositoryId: String) =
        pullRequestRepository.findAllByRepositoryId(repositoryId)

    override fun savePullRequests(pullRequests: List<PullRequest>) {
        pullRequestRepository.saveAll(pullRequests)
    }

    override fun deletePullRequestsByIds(pullRequestsIds: List<String>) =
        pullRequestRepository.deleteAllById(pullRequestsIds)

    override fun findBranchesByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        refRepository.findAllByTypeAndNameContaining(RefType.BRANCH, issueKey)
            .filter { this.containsMatchIn(it.name) }
            .groupByRepository()
    }

    override fun findCommitsByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        commitRepository.findAllByMessageContaining(issueKey)
            .filter { this.containsMatchIn(it.message) }
            .groupByRepository()
    }

    override fun findPullRequestsByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        pullRequestRepository.findAllByTitleContainingOrDescriptionContaining(issueKey, issueKey)
            .filter { this.containsMatchIn(it.title) || this.containsMatchIn(it.description) }
            .groupByRepository()
    }

    override fun findByIssueKey(issueKey: String): SearchSummary {
        val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
        val branchesCommits = refRepository.findAllByTypeAndNameContaining(RefType.BRANCH, issueKey).filter {
            issueKeyRegex.containsMatchIn(it.name)
        }.map {
            //TODO: use commitRepository.findByRepositoryIdAndHash(it.repositoryId, it.hash).firstOrNull() instead?
            commitRepository.findById((object : RepositoryLink(it.repositoryId) {}).id(it.hash)).getOrNull()
        }
        val commits = commitRepository.findAllByMessageContaining(issueKey).filter {
            issueKeyRegex.containsMatchIn(it.message)
        }
        val pullRequests =
            pullRequestRepository.findAllByTitleContainingOrDescriptionContaining(issueKey, issueKey).filter {
                issueKeyRegex.containsMatchIn(it.title) || issueKeyRegex.containsMatchIn(it.description)
            }
        return SearchSummary(
            SearchSummary.SearchBranchesSummary(
                branchesCommits.size,
                branchesCommits.filterNotNull().maxOfOrNull { it.date }
            ),
            SearchSummary.SearchCommitsSummary(
                commits.size,
                commits.maxOfOrNull { it.date }
            ),
            SearchSummary.SearchPullRequestsSummary(
                pullRequests.size,
                pullRequests.maxOfOrNull { it.updatedAt },
                with(pullRequests.map { it.status }.toSet()) {
                    if (this.size == 1) this.first() else null
                })
        )
    }

    private fun <T : RepositoryLink> List<T>.groupByRepository() = this.groupBy {
        it.repositoryId
    }.mapKeys { (repositoryId, _) ->
        repositoryRepository.findById(repositoryId)
    }.filterKeys { repository ->
        repository.isPresent
    }.mapKeys { (repository, _) ->
        repository.get()
    }
}