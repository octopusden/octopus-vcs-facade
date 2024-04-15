package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import kotlin.math.min
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.BaseDocument
import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.repository.CommitRepository
import org.octopusden.octopus.vcsfacade.repository.PullRequestRepository
import org.octopusden.octopus.vcsfacade.repository.RefRepository
import org.octopusden.octopus.vcsfacade.repository.RepositoryRepository
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDto
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class OpenSearchServiceImpl(
    private val repositoryRepository: RepositoryRepository,
    private val refRepository: RefRepository,
    private val commitRepository: CommitRepository,
    private val pullRequestRepository: PullRequestRepository
) : OpenSearchService {
    override fun getRepositories(type: VcsServiceType): Set<RepositoryDocument> {
        log.trace("=> getRepositories({})", type)
        return fetchAll { repositoryRepository.searchFirst1000ByTypeAndIdAfterOrderByIdAsc(type, it) }.also {
            log.trace("<= getRepositories({}): {}", type, it)
        }
    }

    override fun findRepositoryById(repositoryId: String): RepositoryDocument? {
        log.trace("=> findRepositoryById({})", repositoryId)
        return repositoryRepository.findById(repositoryId).getOrNull().also {
            log.trace("<= findRepositoryById({}): {}", repositoryId, it)
        }
    }

    override fun saveRepository(repository: RepositoryDocument): RepositoryDocument {
        log.trace("=> saveRepository({})", repository)
        return repositoryRepository.save(repository).also {
            log.trace("<= saveRepository({}): {}", repository, it)
        }
    }

    override fun deleteRepository(repository: RepositoryDocument) {
        log.trace("=> deleteRepository({})", repository)
        repositoryRepository.delete(repository)
        log.trace("<= deleteRepository({})", repository)
    }

    override fun findRefsByRepositoryId(repositoryId: String): Set<RefDocument> {
        log.trace("=> findRefsByRepositoryId({})", repositoryId)
        return fetchAll { id ->
            refRepository.searchFirst1000ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, id)
        }.also {
            log.trace("<= findRefsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveRefs(refs: List<RefDocument>) {
        log.trace("=> saveRef({})", refs)
        saveAll(refs) { batch -> refRepository.saveAll(batch) }
        log.trace("<= saveRef({})", refs)
    }

    override fun deleteRefsByIds(refsIds: List<String>) {
        log.trace("=> deleteRefsByIds({})", refsIds)
        deleteAll(refsIds) { batch -> refRepository.deleteAllById(batch) }
        log.trace("<= deleteRefsByIds({})", refsIds)
    }

    override fun deleteRefsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteRefsByRepositoryId({})", repositoryId)
        refRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteRefsByRepositoryId({})", repositoryId)
    }

    override fun findCommitsByRepositoryId(repositoryId: String): Set<CommitDocument> {
        log.trace("=> findCommitsByRepositoryId({})", repositoryId)
        return fetchAll { id ->
            commitRepository.searchFirst1000ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, id)
        }.also {
            log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveCommits(commits: List<CommitDocument>) {
        log.trace("=> saveCommits({})", commits)
        saveAll(commits) { batch -> commitRepository.saveAll(batch) }
        log.trace("<= saveCommits({})", commits)
    }

    override fun deleteCommitsByIds(commitsIds: List<String>) {
        log.trace("=> deleteCommitsByIds({})", commitsIds)
        deleteAll(commitsIds) { batch -> commitRepository.deleteAllById(batch) }
        log.trace("<= deleteCommitsByIds({})", commitsIds)
    }

    override fun deleteCommitsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteCommitsByRepositoryId({})", repositoryId)
        commitRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteCommitsByRepositoryId({})", repositoryId)
    }

    override fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequestDocument> {
        log.trace("=> findPullRequestsByRepositoryId({})", repositoryId)
        return fetchAll { id ->
            pullRequestRepository.searchFirst1000ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, id)
        }.also {
            log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun savePullRequests(pullRequests: List<PullRequestDocument>) {
        log.trace("=> savePullRequests({})", pullRequests)
        saveAll(pullRequests) { batch -> pullRequestRepository.saveAll(batch) }
        log.trace("<= savePullRequests({})", pullRequests)
    }

    override fun deletePullRequestsByIds(pullRequestsIds: List<String>) {
        log.trace("=> deletePullRequestsByIds({})", pullRequestsIds)
        deleteAll(pullRequestsIds) { batch -> pullRequestRepository.deleteAllById(batch) }
        log.trace("<= deletePullRequestsByIds({})", pullRequestsIds)
    }

    override fun deletePullRequestsByRepositoryId(repositoryId: String) {
        log.trace("=> deletePullRequestsByRepositoryId({})", repositoryId)
        pullRequestRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deletePullRequestsByRepositoryId({})", repositoryId)
    }

    override fun findBranchesByIssueKey(issueKey: String): List<Branch> {
        log.trace("=> findBranchesByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey)
                .filter { containsMatchIn(it.name) }
                .map { it.toDto() as Branch }
        }.also {
            log.trace("<= findBranchesByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findCommitsByIssueKey(issueKey: String): List<Commit> {
        log.trace("=> findCommitsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            commitRepository.searchByMessageContaining(issueKey)
                .filter { containsMatchIn(it.message) }
                .map { it.toDto() }
        }.also {
            log.trace("<= findCommitsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findPullRequestsByIssueKey(issueKey: String): List<PullRequest> {
        log.trace("=> findPullRequestsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey)
                .filter { containsMatchIn(it.title) || containsMatchIn(it.description) }
                .map { it.toDto() }
        }.also {
            log.trace("<= findPullRequestsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findByIssueKey(issueKey: String): SearchSummary {
        log.trace("=> findByIssueKey({})", issueKey)
        val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
        val branchesCommits = refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey).filter {
            issueKeyRegex.containsMatchIn(it.name)
        }.map {
            commitRepository.findById(it.commitId).getOrNull()
        }
        val commits = commitRepository.searchByMessageContaining(issueKey).filter {
            issueKeyRegex.containsMatchIn(it.message)
        }
        val pullRequests =
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey).filter {
                issueKeyRegex.containsMatchIn(it.title) || issueKeyRegex.containsMatchIn(it.description)
            }
        return SearchSummary(SearchSummary.SearchBranchesSummary(
            branchesCommits.size,
            branchesCommits.filterNotNull().maxOfOrNull { it.date }),
            SearchSummary.SearchCommitsSummary(commits.size, commits.maxOfOrNull { it.date }),
            SearchSummary.SearchPullRequestsSummary(pullRequests.size,
                pullRequests.maxOfOrNull { it.updatedAt },
                with(pullRequests.map { it.status }.toSet()) {
                    if (size == 1) first() else null
                })
        ).also {
            log.trace("<= findByIssueKey({}): {}", issueKey, it)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpenSearchServiceImpl::class.java)

        private const val BATCH_SIZE = 1000 //must be equal to search limit in repositories

        /* IMPORTANT: use raw `search_after` approach because:
         * - native query builder required to use `search_after` with PIT or to `scroll` (spring-data-opensearch does not fully support Spring Data JPA Scroll API)
         * - limitation of raw `search_after` (inconsistency because of concurrent operations) is suitable, consistency is complied by vcs services
         * - better performance of raw `search_after` comparing with `search_after` with PIT or `scroll`
         */
        private fun <T : BaseDocument> fetchAll(fetchBatchAfterId: (id: String) -> List<T>): Set<T> {
            val documents = mutableSetOf<T>()
            var lastId = ""
            do {
                val batch = fetchBatchAfterId.invoke(lastId)
                if (batch.isEmpty()) break
                documents.addAll(batch)
                lastId = batch.last().id
            } while (batch.size == BATCH_SIZE)
            return documents
        }

        private fun <T : BaseDocument> saveAll(documents: List<T>, saveBatch: (batch: List<T>) -> Unit) {
            if (documents.isNotEmpty()) {
                var page = 0
                do {
                    saveBatch.invoke(documents.subList(page * BATCH_SIZE, min(++page * BATCH_SIZE, documents.size)))
                } while (page * BATCH_SIZE < documents.size)
            }
        }

        private fun deleteAll(ids: List<String>, deleteBatch: (batch: List<String>) -> Unit) {
            if (ids.isNotEmpty()) {
                var page = 0
                do {
                    deleteBatch.invoke(ids.subList(page * BATCH_SIZE, min(++page * BATCH_SIZE, ids.size)))
                } while (page * BATCH_SIZE < ids.size)
            }
        }
    }
}