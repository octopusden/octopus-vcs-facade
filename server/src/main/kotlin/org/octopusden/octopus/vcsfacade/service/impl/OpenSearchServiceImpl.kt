package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.BaseDocument
import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.repository.CommitRepository
import org.octopusden.octopus.vcsfacade.repository.PullRequestRepository
import org.octopusden.octopus.vcsfacade.repository.RefRepository
import org.octopusden.octopus.vcsfacade.repository.RepositoryInfoRepository
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
    override fun getRepositoriesInfo(scanRequired: Boolean?): Set<RepositoryInfoDocument> {
        log.trace("=> getRepositoriesInfo({})", scanRequired)
        val repositoriesInfo = if (scanRequired == null) {
            fetchAll { repositoryInfoRepository.searchFirst50ByIdAfterOrderByIdAsc(it) }
        } else {
            fetchAll { repositoryInfoRepository.searchFirst50ByScanRequiredAndIdAfterOrderByIdAsc(scanRequired, it) }
        }
        log.trace("<= getRepositoriesInfo({}): {}", scanRequired, repositoriesInfo)
        return repositoriesInfo
    }

    override fun findRepositoryInfoById(repositoryId: String): RepositoryInfoDocument? {
        log.trace("=> findRepositoryInfoByRepositoryId({})", repositoryId)
        return repositoryInfoRepository.findById(repositoryId).getOrNull().also {
            log.trace("<= findRepositoryInfoByRepositoryId({}): {}", repositoryId, it)
        }
    }

    override fun saveRepositoriesInfo(repositoriesInfo: Sequence<RepositoryInfoDocument>): Set<String> {
        if (log.isTraceEnabled) log.trace("=> saveRepositoriesInfo({})", repositoriesInfo.toList())
        return processAll(repositoriesInfo) { repositoryInfoRepository.saveAll(it) }.also {
            if (log.isTraceEnabled) log.trace("<= saveRepositoriesInfo({}): {}", repositoriesInfo.toList(), it)
        }
    }

    override fun deleteRepositoryInfoById(repositoryId: String) {
        log.trace("=> deleteRepositoryInfo({})", repositoryId)
        repositoryInfoRepository.deleteById(repositoryId)
        log.trace("<= deleteRepositoryInfo({})", repositoryId)
    }

    override fun findRefsIdsByRepositoryId(repositoryId: String): Set<String> {
        log.trace("=> findRefsByRepositoryId({})", repositoryId)
        return fetchAllIds { refRepository.searchFirst50ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it) }
            .also { log.trace("<= findRefsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun saveRefs(refs: Sequence<RefDocument>): Set<String> {
        if (log.isTraceEnabled) log.trace("=> saveRef({})", refs.toList())
        return processAll(refs) { refRepository.saveAll(it) }.also {
            if (log.isTraceEnabled) log.trace("<= saveRef({}): {}", refs.toList(), it)
        }
    }

    override fun deleteRefsByIds(refsIds: Set<String>) {
        log.trace("=> deleteRefsByIds({})", refsIds)
        processAllIds(refsIds) { refRepository.deleteAllById(it) }
        log.trace("<= deleteRefsByIds({})", refsIds)
    }

    override fun deleteRefsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteRefsByRepositoryId({})", repositoryId)
        refRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteRefsByRepositoryId({})", repositoryId)
    }

    override fun findCommitsIdsByRepositoryId(repositoryId: String): Set<String> {
        log.trace("=> findCommitsByRepositoryId({})", repositoryId)
        return fetchAllIds { commitRepository.searchFirst50ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it) }
            .also { log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun saveCommits(commits: Sequence<CommitDocument>): Set<String> {
        if (log.isTraceEnabled) log.trace("=> saveCommits({})", commits.toList())
        return processAll(commits, 500, { 1 + it.files.size }) { commitRepository.saveAll(it) }.also {
            if (log.isTraceEnabled) log.trace("<= saveCommits({}): {}", commits.toList(), it)
        }
    }

    override fun deleteCommitsByIds(commitsIds: Set<String>) {
        log.trace("=> deleteCommitsByIds({})", commitsIds)
        processAllIds(commitsIds) { commitRepository.deleteAllById(it) }
        log.trace("<= deleteCommitsByIds({})", commitsIds)
    }

    override fun deleteCommitsByRepositoryId(repositoryId: String) {
        log.trace("=> deleteCommitsByRepositoryId({})", repositoryId)
        commitRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deleteCommitsByRepositoryId({})", repositoryId)
    }

    override fun findPullRequestsIdsByRepositoryId(repositoryId: String): Set<String> {
        log.trace("=> findPullRequestsByRepositoryId({})", repositoryId)
        return fetchAllIds {
            pullRequestRepository.searchFirst50ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId, it)
        }.also { log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, it) }
    }

    override fun savePullRequests(pullRequests: Sequence<PullRequestDocument>): Set<String> {
        if (log.isTraceEnabled) log.trace("=> savePullRequests({})", pullRequests.toList())
        return processAll(pullRequests) { pullRequestRepository.saveAll(it) }.also {
            if (log.isTraceEnabled) log.trace("<= savePullRequests({}): {}", pullRequests.toList(), it)
        }
    }

    override fun deletePullRequestsByIds(pullRequestsIds: Set<String>) {
        log.trace("=> deletePullRequestsByIds({})", pullRequestsIds)
        processAllIds(pullRequestsIds) { pullRequestRepository.deleteAllById(it) }
        log.trace("<= deletePullRequestsByIds({})", pullRequestsIds)
    }

    override fun deletePullRequestsByRepositoryId(repositoryId: String) {
        log.trace("=> deletePullRequestsByRepositoryId({})", repositoryId)
        pullRequestRepository.deleteByRepositoryId(repositoryId)
        log.trace("<= deletePullRequestsByRepositoryId({})", repositoryId)
    }

    override fun findBranchesByIssueKeys(issueKeys: Set<String>): Set<RefDocument> {
        log.trace("=> findBranchesByIssueKeys({})", issueKeys)
        return issueKeys.flatMap { issueKey ->
            val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
            refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey)
                .filter { issueKeyRegex.containsMatchIn(it.name) }
        }.toSet().also {
            log.trace("<= findBranchesByIssueKeys({}): {}", issueKeys, it)
        }
    }

    override fun findCommitsByIssueKeys(issueKeys: Set<String>): Set<CommitDocument> {
        log.trace("=> findCommitsByIssueKeys({})", issueKeys)
        return issueKeys.flatMap { issueKey ->
            val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
            commitRepository.searchByMessageContaining(issueKey)
                .filter { issueKeyRegex.containsMatchIn(it.message) }
        }.toSet().also {
            log.trace("<= findCommitsByIssueKeys({}): {}", issueKeys, it)
        }
    }

    override fun findPullRequestsByIssueKeys(issueKeys: Set<String>): Set<PullRequestDocument> {
        log.trace("=> findPullRequestsByIssueKeys({})", issueKeys)
        return issueKeys.flatMap { issueKey ->
            val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey)
                .filter { issueKeyRegex.containsMatchIn(it.title) || issueKeyRegex.containsMatchIn(it.description) }
        }.toSet().also {
            log.trace("<= findPullRequestsByIssueKeys({}): {}", issueKeys, it)
        }
    }

    override fun findByIssueKeys(issueKeys: Set<String>): SearchSummary {
        log.trace("=> findByIssueKeys({})", issueKeys)
        val issueKeysToRegex = issueKeys.map { it to IssueKeyParser.getIssueKeyRegex(it) }
        val branchesCommits = issueKeysToRegex.flatMap { (issueKey, regex) ->
            refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey).filter {
                regex.containsMatchIn(it.name)
            }.map {
                commitRepository.findById(it.commitId).getOrNull()
            }
        }.toSet()
        val commits = issueKeysToRegex.flatMap { (issueKey, regex) ->
            commitRepository.searchByMessageContaining(issueKey).filter {
                regex.containsMatchIn(it.message)
            }
        }.toSet()
        val pullRequests = issueKeysToRegex.flatMap { (issueKey, regex) ->
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey).filter {
                regex.containsMatchIn(it.title) || regex.containsMatchIn(it.description)
            }
        }.toSet()
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
            log.trace("<= findByIssueKeys({}): {}", issueKeys, it)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpenSearchServiceImpl::class.java)

        private const val BATCH_SIZE = 50 //must be equal to search limit in repositories

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

        private fun <T : BaseDocument> fetchAllIds(fetchBatchAfterId: (id: String) -> List<T>): Set<String> {
            val documentsIds = mutableSetOf<String>()
            var lastId = ""
            do {
                val batch = fetchBatchAfterId.invoke(lastId)
                if (batch.isEmpty()) break
                batch.forEach { documentsIds.add(it.id) }
                lastId = batch.last().id
            } while (batch.size == BATCH_SIZE)
            return documentsIds
        }

        private fun <T : BaseDocument> processAll(documents: Sequence<T>, batchOperation: (batch: List<T>) -> Unit) =
            processAll(documents, BATCH_SIZE, { 1 }, batchOperation)

        private fun <T : BaseDocument> processAll(
            documents: Sequence<T>,
            batchWeightLimit: Int,
            documentWeight: (document: T) -> Int,
            batchOperation: (batch: List<T>) -> Unit
        ): Set<String> {
            val documentsIds = mutableSetOf<String>()
            val batch = ArrayList<T>(BATCH_SIZE)
            var batchWeight = 0
            for (document in documents) {
                batchWeight += documentWeight.invoke(document)
                batch.add(document)
                if (batch.size == BATCH_SIZE || batchWeight >= batchWeightLimit) {
                    batchOperation.invoke(batch)
                    batch.forEach { documentsIds.add(it.id) }
                    batch.clear()
                    batchWeight = 0
                }
            }
            if (batch.isNotEmpty()) {
                batchOperation.invoke(batch)
                batch.forEach { documentsIds.add(it.id) }
            }
            return documentsIds
        }

        private fun processAllIds(documentsIds: Set<String>, batchOperation: (batch: List<String>) -> Unit) =
            documentsIds.chunked(BATCH_SIZE).forEach {
                batchOperation.invoke(it)
            }

    }
}