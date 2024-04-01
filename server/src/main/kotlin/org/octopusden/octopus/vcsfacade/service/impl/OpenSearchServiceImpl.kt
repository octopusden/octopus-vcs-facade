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
import org.slf4j.LoggerFactory
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
    override fun getRepositories(): List<Repository> {
        log.trace("=> getRepositories()")
        return repositoryRepository.findAll().toList().also {
            log.trace("<= getRepositories(): {}", it)
        }
    }

    override fun getRepositories(type: VcsServiceType): List<Repository> {
        log.trace("=> getRepositories({})", type)
        return repositoryRepository.findByType(type).also {
            log.trace("<= getRepositories({}): {}", type, it)
        }
    }

    override fun findRepositoryById(repositoryId: String): Repository? {
        log.trace("=> findRepositoryById({})", repositoryId)
        return repositoryRepository.findById(repositoryId).getOrNull().also {
            log.trace("<= findRepositoryById({}): {}", repositoryId, it)
        }
    }

    override fun saveRepository(repository: Repository): Repository {
        log.trace("=> saveRepository({})", repository)
        return repositoryRepository.save(repository).also {
            log.trace("<= saveRepository({}): {}", repository, it)
        }
    }

    override fun deleteRepository(repository: Repository) {
        log.trace("=> deleteRepository({})", repository)
        repositoryRepository.delete(repository)
        log.trace("<= deleteRepository({})", repository)
    }

    override fun findRefsByRepositoryId(repositoryId: String): List<Ref> {
        log.trace("=> findRefsByRepositoryId({})", repositoryId)
        return refRepository.findByRepositoryId(repositoryId).also {
            log.trace("<= findRefsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveRefs(refs: List<Ref>) {
        log.trace("=> saveRef({})", refs)
        refRepository.saveAll(refs)
        log.trace("<= saveRef({})", refs)
    }

    override fun deleteRefsByIds(refsIds: List<String>) {
        log.trace("=> deleteRefsByIds({})", refsIds)
        refRepository.deleteAllById(refsIds)
        log.trace("<= deleteRefsByIds({})", refsIds)
    }

    override fun deleteRefsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteRefsByRepositoryId({})", repositoryId)
        refRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteRefsByRepositoryId({})", repositoryId)
    }

    override fun findCommitsByRepositoryId(repositoryId: String): List<Commit> {
        log.trace("=> findCommitsByRepositoryId({})", repositoryId)
        return commitRepository.findByRepositoryId(repositoryId).also {
            log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveCommits(commits: List<Commit>) {
        log.trace("=> saveCommits({})", commits)
        commitRepository.saveAll(commits)
        log.trace("<= saveCommits({})", commits)
    }

    override fun deleteCommitsByIds(commitsIds: List<String>) {
        log.trace("=> deleteCommitsByIds({})", commitsIds)
        commitRepository.deleteAllById(commitsIds)
        log.trace("<= deleteCommitsByIds({})", commitsIds)
    }

    override fun deleteCommitsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteCommitsByRepositoryId({})", repositoryId)
        commitRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteCommitsByRepositoryId({})", repositoryId)
    }

    override fun findPullRequestsByRepositoryId(repositoryId: String): List<PullRequest> {
        log.trace("=> findPullRequestsByRepositoryId({})", repositoryId)
        return pullRequestRepository.findByRepositoryId(repositoryId).also {
            log.trace("<= findPullRequestsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun savePullRequests(pullRequests: List<PullRequest>) {
        log.trace("=> savePullRequests({})", pullRequests)
        pullRequestRepository.saveAll(pullRequests)
        log.trace("<= savePullRequests({})", pullRequests)
    }

    override fun deletePullRequestsByIds(pullRequestsIds: List<String>) {
        log.trace("=> deletePullRequestsByIds({})", pullRequestsIds)
        pullRequestRepository.deleteAllById(pullRequestsIds)
        log.trace("<= deletePullRequestsByIds({})", pullRequestsIds)
    }

    override fun deletePullRequestsByRepositoryId(repositoryId: String) {
        log.trace("=> deletePullRequestsByRepositoryId({})", repositoryId)
        pullRequestRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deletePullRequestsByRepositoryId({})", repositoryId)
    }

    override fun findBranchesByIssueKey(issueKey: String): Map<Repository, List<Ref>> {
        log.trace("=> findBranchesByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            refRepository.findByTypeAndNameContaining(RefType.BRANCH, issueKey)
                .filter { containsMatchIn(it.name) }
                .groupByRepository()
        }.also {
            log.trace("<= findBranchesByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findCommitsByIssueKey(issueKey: String): Map<Repository, List<Commit>> {
        log.trace("=> findCommitsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            commitRepository.findByMessageContaining(issueKey)
                .filter { containsMatchIn(it.message) }
                .groupByRepository()
        }.also {
            log.trace("<= findCommitsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findPullRequestsByIssueKey(issueKey: String): Map<Repository, List<PullRequest>> {
        log.trace("=> findPullRequestsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            pullRequestRepository.findByTitleContainingOrDescriptionContaining(issueKey, issueKey)
                .filter { containsMatchIn(it.title) || containsMatchIn(it.description) }
                .groupByRepository()
        }.also {
            log.trace("<= findPullRequestsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findByIssueKey(issueKey: String): SearchSummary {
        log.trace("=> findByIssueKey({})", issueKey)
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
        ).also {
            log.trace("<= findByIssueKey({}): {}", issueKey, it)
        }
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

    companion object {
        private val log = LoggerFactory.getLogger(OpenSearchServiceImpl::class.java)
    }
}