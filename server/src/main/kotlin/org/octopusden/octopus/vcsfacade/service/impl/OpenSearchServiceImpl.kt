package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
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
    /* IMPORTANT: use raw `search_after` approach because:
     * - native query builder required to use `search_after` with PIT or to `scroll` (spring-data-opensearch does not fully support Spring Data JPA Scroll API)
     * - limitation of raw `search_after` (inconsistency because of concurrent operations) is suitable, consistency is complied by vcs services
     * - better performance of raw `search_after` comparing with `search_after` with PIT or `scroll`
     */

    override fun getRepositories(type: VcsServiceType): Set<Repository> {
        log.trace("=> getRepositories({})", type)
        val repositories = mutableSetOf<Repository>()
        var lastRepository: Repository? = null
        do {
            val repositoryBatch =
                repositoryRepository.searchFirst100ByTypeAndGroupAfterAndNameAfterOrderByGroupAscNameAsc(
                    type, lastRepository?.group ?: "", lastRepository?.name ?: ""
                )
            if (repositoryBatch.isEmpty()) break
            repositories.addAll(repositoryBatch)
            lastRepository = repositoryBatch.last()
        } while (repositoryBatch.size == 100)
        log.trace("<= getRepositories({}): {}", type, repositories)
        return repositories
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

    override fun findRefsByRepositoryId(repositoryId: String): Set<Ref> {
        log.trace("=> findRefsByRepositoryId({})", repositoryId)
        val refs = mutableSetOf<Ref>()
        var lastBranchHash = ""
        do {
            val branchBatch = refRepository.searchFirst100ByRepositoryIdAndTypeAndHashAfterOrderByHashAsc(
                repositoryId, RefType.BRANCH, lastBranchHash
            )
            if (branchBatch.isEmpty()) break
            refs.addAll(branchBatch)
            if (lastBranchHash == branchBatch.last().hash) {
                log.error("100 or over branches detected with hash $lastBranchHash for repositoryId $repositoryId")
                break
            }
            lastBranchHash = branchBatch.last().hash
        } while (branchBatch.size == 100)
        var lastTagHash = ""
        do {
            val tagBatch = refRepository.searchFirst100ByRepositoryIdAndTypeAndHashAfterOrderByHashAsc(
                repositoryId, RefType.TAG, lastTagHash
            )
            if (tagBatch.isEmpty()) break
            refs.addAll(tagBatch)
            if (lastTagHash == tagBatch.last().hash) {
                log.error("100 or over tags detected with hash $lastTagHash for repositoryId $repositoryId")
                break
            }
            lastTagHash = tagBatch.last().hash
        } while (tagBatch.size == 100)
        log.trace("<= findRefsByRepositoryId({}): {}", repositoryId, refs)
        return refs
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

    override fun findCommitsByRepositoryId(repositoryId: String): Set<Commit> {
        log.trace("=> findCommitsByRepositoryId({})", repositoryId)
        val commits = mutableSetOf<Commit>()
        var lastCommitHash = ""
        do {
            val commitBatch = commitRepository.searchFirst100ByRepositoryIdAndHashAfterOrderByHashAsc(
                repositoryId, lastCommitHash
            )
            if (commitBatch.isEmpty()) break
            commits.addAll(commitBatch)
            lastCommitHash = commitBatch.last().hash
        } while (commitBatch.size == 100)
        log.trace("<= findCommitsByRepositoryId({}): {}", repositoryId, commits)
        return commits
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

    override fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequest> {
        log.trace("=> findPullRequestsByRepositoryId({})", repositoryId)
        val pullRequests = mutableSetOf<PullRequest>()
        var lastPullRequestIndex = 0L
        do {
            val pullRequestBatch = pullRequestRepository.searchFirst100ByRepositoryIdAndIndexAfterOrderByIndexAsc(
                repositoryId, lastPullRequestIndex
            )
            if (pullRequestBatch.isEmpty()) break
            pullRequests.addAll(pullRequestBatch)
            lastPullRequestIndex = pullRequestBatch.last().index
        } while (pullRequestBatch.size == 100)
        log.trace("<= findPullRequestsByRepositoryId({}): {}", repositoryId, pullRequests)
        return pullRequests
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

    override fun findBranchesByIssueKey(issueKey: String): List<Ref> {
        log.trace("=> findBranchesByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            refRepository.searchByTypeAndNameContaining(RefType.BRANCH, issueKey).filter { containsMatchIn(it.name) }
        }.also {
            log.trace("<= findBranchesByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findCommitsByIssueKey(issueKey: String): List<Commit> {
        log.trace("=> findCommitsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            commitRepository.searchByMessageContaining(issueKey).filter { containsMatchIn(it.message) }
        }.also {
            log.trace("<= findCommitsByIssueKey({}): {}", issueKey, it)
        }
    }

    override fun findPullRequestsByIssueKey(issueKey: String): List<PullRequest> {
        log.trace("=> findPullRequestsByIssueKey({})", issueKey)
        return with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
            pullRequestRepository.searchByTitleContainingOrDescriptionContaining(issueKey, issueKey)
                .filter { containsMatchIn(it.title) || containsMatchIn(it.description) }
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
        return SearchSummary(SearchSummary.SearchBranchesSummary(branchesCommits.size,
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
    }
}