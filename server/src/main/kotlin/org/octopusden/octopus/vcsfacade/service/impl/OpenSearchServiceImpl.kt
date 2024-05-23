package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.BaseDocument
import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.repository.CommitRepository
import org.octopusden.octopus.vcsfacade.repository.PullRequestRepository
import org.octopusden.octopus.vcsfacade.repository.RefRepository
import org.octopusden.octopus.vcsfacade.repository.RepositoryInfoRepository
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class OpenSearchServiceImpl(
    private val repositoryInfoRepository: RepositoryInfoRepository,
    private val refRepository: RefRepository,
    private val commitRepository: CommitRepository,
    private val pullRequestRepository: PullRequestRepository
) : OpenSearchService {
    override fun findRepositoriesInfoByRepositoryType(type: VcsServiceType): Set<RepositoryInfoDocument> {
        log.trace("=> findRepositoriesInfoByRepositoryType({})", type)
        return fetchAll { repositoryInfoRepository.searchFirst100ByRepositoryTypeAndIdAfterOrderByIdAsc(type, it) }
            .also { log.trace("<= findRepositoriesInfoByRepositoryType({}): {}", type, it) }
    }

    override fun findRepositoryInfoById(repositoryId: String): RepositoryInfoDocument? {
        log.trace("=> findRepositoryInfoByRepositoryId({})", repositoryId)
        return repositoryInfoRepository.findById(repositoryId).getOrNull().also {
            log.trace("<= findRepositoryInfoByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveRepositoriesInfo(repositoriesInfo: Sequence<RepositoryInfoDocument>) {
        log.trace("=> saveRepositoriesInfo({})", repositoriesInfo)
        processAll(repositoriesInfo) { batch -> repositoryInfoRepository.saveAll(batch) }
        log.trace("<= saveRepositoriesInfo({})", repositoriesInfo)
    }

    override fun deleteRepositoryInfoById(repositoryId: String) {
        log.trace("=> deleteRepositoryInfo({})", repositoryId)
        repositoryInfoRepository.deleteById(repositoryId)
        log.trace("<= deleteRepositoryInfo({})", repositoryId)
    }

    override fun findRefsByRepositoryId(repositoryId: String): Set<RefDocument> {
        log.trace("=> findRefsByRepositoryId({})", repositoryId)
        return fetchAll { refRepository.searchFirst100ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it) }
            .also { log.trace("<= findRefsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun saveRefs(refs: Sequence<RefDocument>) {
        log.trace("=> saveRef({})", refs)
        processAll(refs) { batch -> refRepository.saveAll(batch) }
        log.trace("<= saveRef({})", refs)
    }

    override fun deleteRefsByIds(refsIds: Sequence<String>) {
        log.trace("=> deleteRefsByIds({})", refsIds)
        processAll(refsIds) { batch -> refRepository.deleteAllById(batch) }
        log.trace("<= deleteRefsByIds({})", refsIds)
    }

    override fun deleteRefsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteRefsByRepositoryId({})", repositoryId)
        refRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteRefsByRepositoryId({})", repositoryId)
    }

    override fun findCommitsByRepositoryId(repositoryId: String): Set<CommitDocument> {
        log.trace("=> findCommitsByRepositoryId({})", repositoryId)
        return fetchAll { commitRepository.searchFirst100ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it) }
            .also { log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun saveCommits(commits: Sequence<CommitDocument>) {
        log.trace("=> saveCommits({})", commits)
        processAll(commits) { batch -> commitRepository.saveAll(batch) }
        log.trace("<= saveCommits({})", commits)
    }

    override fun deleteCommitsByIds(commitsIds: Sequence<String>) {
        log.trace("=> deleteCommitsByIds({})", commitsIds)
        processAll(commitsIds) { batch -> commitRepository.deleteAllById(batch) }
        log.trace("<= deleteCommitsByIds({})", commitsIds)
    }

    override fun deleteCommitsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteCommitsByRepositoryId({})", repositoryId)
        commitRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteCommitsByRepositoryId({})", repositoryId)
    }

    override fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequestDocument> {
        log.trace("=> findPullRequestsByRepositoryId({})", repositoryId)
        return fetchAll { pullRequestRepository.searchFirst100ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it) }
            .also { log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun savePullRequests(pullRequests: Sequence<PullRequestDocument>) {
        log.trace("=> savePullRequests({})", pullRequests)
        processAll(pullRequests) { batch -> pullRequestRepository.saveAll(batch) }
        log.trace("<= savePullRequests({})", pullRequests)
    }

    override fun deletePullRequestsByIds(pullRequestsIds: Sequence<String>) {
        log.trace("=> deletePullRequestsByIds({})", pullRequestsIds)
        processAll(pullRequestsIds) { batch -> pullRequestRepository.deleteAllById(batch) }
        log.trace("<= deletePullRequestsByIds({})", pullRequestsIds)
    }

    override fun deletePullRequestsByRepositoryId(repositoryId: String) {
        log.trace("=> deletePullRequestsByRepositoryId({})", repositoryId)
        pullRequestRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deletePullRequestsByRepositoryId({})", repositoryId)
    }

    override fun findBranchesByIssueKey(issueKey: String): Sequence<RefDocument> {
        log.trace("=> findBranchesByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey)
                .asSequence().filter { containsMatchIn(it.name) }
        }.also {
            log.trace("<= findBranchesByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findCommitsByIssueKey(issueKey: String): Sequence<CommitDocument> {
        log.trace("=> findCommitsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            commitRepository.searchByMessageContaining(issueKey)
                .asSequence().filter { containsMatchIn(it.message) }
        }.also {
            log.trace("<= findCommitsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findPullRequestsByIssueKey(issueKey: String): Sequence<PullRequestDocument> {
        log.trace("=> findPullRequestsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey)
                .asSequence().filter { containsMatchIn(it.title) || containsMatchIn(it.description) }
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

        private const val BATCH_SIZE = 100 //must be equal to search limit in repositories

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

        private fun <T> processAll(documents: Sequence<T>, batchOperation: (batch: List<T>) -> Unit) {
            fun batchOperationWithFallback(batch: List<T>) = try {
                batchOperation.invoke(batch)
            } catch (e: UncategorizedElasticsearchException) {
                //fallback if batch size is still too large (because of non-average size of some documents)
                batch.forEach { batchOperation(listOf(it)) }
            }

            val batch = ArrayList<T>(BATCH_SIZE)
            for (document in documents) {
                batch.add(document)
                if (batch.size == BATCH_SIZE) {
                    batchOperationWithFallback(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                batchOperationWithFallback(batch)
            }
        }
    }
}