package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.document.RepositoryLink
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
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
    override fun getRepositories() = repositoryRepository.findAll().toList()

    override fun getRepositories(type: VcsServiceType) = repositoryRepository.findByType(type)

    override fun findRepositoryById(repositoryId: String) =
        repositoryRepository.findById(repositoryId).getOrNull()

    override fun saveRepository(repository: Repository) =
        repositoryRepository.save(repository)

    override fun deleteRepository(repository: Repository) =
        repositoryRepository.delete(repository)

    override fun findRefsByRepositoryId(repositoryId: String) =
        refRepository.findByRepositoryId(repositoryId)

    override fun saveRefs(refs: List<Ref>) {
        refRepository.saveAll(refs)
    }

    override fun deleteRefsByIds(refsIds: List<String>) =
        refRepository.deleteAllById(refsIds)

    override fun deleteRefsByRepositoryId(repositoryId: String) =
        refRepository.deleteByRepositoryId(repositoryId)

    override fun findCommitsByRepositoryId(repositoryId: String) =
        commitRepository.findByRepositoryId(repositoryId)

    override fun saveCommits(commits: List<Commit>) {
        commitRepository.saveAll(commits)
    }

    override fun deleteCommitsByIds(commitsIds: List<String>) =
        commitRepository.deleteAllById(commitsIds)

    override fun deleteCommitsByRepositoryId(repositoryId: String) =
        commitRepository.deleteByRepositoryId(repositoryId)

    override fun findPullRequestsByRepositoryId(repositoryId: String) =
        pullRequestRepository.findByRepositoryId(repositoryId)

    override fun savePullRequests(pullRequests: List<PullRequest>) {
        pullRequestRepository.saveAll(pullRequests)
    }

    override fun deletePullRequestsByIds(pullRequestsIds: List<String>) =
        pullRequestRepository.deleteAllById(pullRequestsIds)

    override fun deletePullRequestsByRepositoryId(repositoryId: String) =
        pullRequestRepository.deleteByRepositoryId(repositoryId)

    override fun findBranchesByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        refRepository.findByTypeAndNameContaining(RefType.BRANCH, issueKey)
            .filter { containsMatchIn(it.name) }
            .groupByRepository()
    }

    override fun findCommitsByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        commitRepository.findByMessageContaining(issueKey)
            .filter { containsMatchIn(it.message) }
            .groupByRepository()
    }

    override fun findPullRequestsByIssueKey(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        pullRequestRepository.findByTitleContainingOrDescriptionContaining(issueKey, issueKey)
            .filter { containsMatchIn(it.title) || containsMatchIn(it.description) }
            .groupByRepository()
    }

    override fun findByIssueKey(issueKey: String): SearchSummary {
        val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
        val branchesCommits = refRepository.findByTypeAndNameContaining(RefType.BRANCH, issueKey).filter {
            issueKeyRegex.containsMatchIn(it.name)
        }.map {
            //TODO: use commitRepository.findByRepositoryIdAndHash(it.repositoryId, it.hash).firstOrNull() instead?
            commitRepository.findById((object : RepositoryLink(it.repositoryId) {}).id(it.hash)).getOrNull()
        }
        val commits = commitRepository.findByMessageContaining(issueKey).filter {
            issueKeyRegex.containsMatchIn(it.message)
        }
        val pullRequests =
            pullRequestRepository.findByTitleContainingOrDescriptionContaining(issueKey, issueKey).filter {
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
                    if (size == 1) first() else null
                })
        )
    }

    private fun <T : RepositoryLink> List<T>.groupByRepository() = groupBy {
        it.repositoryId
    }.mapKeys { (repositoryId, _) ->
        repositoryRepository.findById(repositoryId)
    }.filterKeys { repository ->
        repository.isPresent
    }.mapKeys { (repository, _) ->
        repository.get()
    }
}