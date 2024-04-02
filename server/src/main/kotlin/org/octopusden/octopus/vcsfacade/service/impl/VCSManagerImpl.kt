package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.stream.Collectors
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.VCSManager
import org.octopusden.octopus.vcsfacade.service.VCSService
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class VCSManagerImpl(
    private val vcsServices: List<VCSService>,
    private val vcsProperties: List<VCSConfig.VCSProperties>,
    private val openSearchService: OpenSearchService?
) : VCSManager, HealthIndicator {
    override fun getTags(sshUrl: String): List<Tag> {
        log.trace("=> getTags({})", sshUrl)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTags(group, repository)
        }.also {
            log.trace("<= getTags({}): {}", sshUrl, it)
        }
    }

    override fun getCommits(sshUrl: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", sshUrl, fromId, fromDate, toId)
        val vcsService = getVcsService(sshUrl)
        val (group, repository) = vcsService.parse(sshUrl)
        val commits = if (fromId != null) {
            if (fromDate != null) {
                throw ArgumentsNotCompatibleException("Params 'fromId' and 'fromDate' can not be used together")
            }
            if (fromId == toId) {
                emptyList()
            } else {
                vcsService.getCommits(group, repository, toId, fromId)
            }
        } else {
            vcsService.getCommits(group, repository, toId, fromDate)
        }
        log.trace("<= getCommits({}, {}, {}, {}): {}", sshUrl, fromId, fromDate, toId, commits)
        return commits
    }

    override fun getCommit(sshUrl: String, id: String): Commit {
        log.trace("=> getCommit({}, {})", sshUrl, id)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommit(group, repository, id)
        }.also {
            log.trace("<= getCommit({}, {}): {}", sshUrl, id, it)
        }
    }

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest {
        log.trace("=> createPullRequest({}, {})", sshUrl, createPullRequest)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            createPullRequest(group, repository, createPullRequest)
        }.also {
            log.trace("<= createPullRequest({}, {}): {}", sshUrl, createPullRequest, it)
        }
    }

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse {
        log.trace("=> searchIssuesInRanges({})", searchRequest)
        val messageRanges = searchRequest.ranges.flatMap { range ->
            getCommits(
                range.sshUrl, range.fromCid, range.fromDate, range.toCid
            ).map { commit -> commit.message to range }
        }.groupBy({ (message, _) -> message }, { (_, range) -> range })
        return SearchIssueInRangesResponse(searchRequest.issues.map { issue ->
            val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issue)
            issue to messageRanges.entries.filter { (message, _) -> issueKeyRegex.containsMatchIn(message) }
                .flatMap { (_, ranges) -> ranges }
        }.groupBy({ (issue, _) -> issue }, { (_, ranges) -> ranges }).entries.stream()
            .collect(
                Collectors.toMap({ (issue, _) -> issue },
                    { (_, ranges) -> ranges.flatten().toSet() },
                    { a, b -> a + b })
            ).filter { (_, ranges) -> ranges.isNotEmpty() }
        ).also {
            log.trace("<= searchIssuesInRanges({}): {}", searchRequest, it)
        }
    }

    override fun findBranches(issueKey: String): List<Branch> {
        log.trace("=> findBranches({})", issueKey)
        val branches = openSearchService?.findBranchesByIssueKey(issueKey)?.map { (repository, refs) ->
            getVcsService(repository.type)?.let { vcsService ->
                vcsService.findBranches(repository.group, repository.name, refs.map { ref -> ref.name }.toSet())
            } ?: emptyList()
        }?.flatten() ?: vcsServices.flatMap { it.findBranches(issueKey) }
        log.trace("<= findBranches({}): {}", issueKey, branches)
        return branches
    }

    override fun findCommits(issueKey: String): List<Commit> {
        log.trace("=> findCommits({})", issueKey)
        val commits = openSearchService?.findCommitsByIssueKey(issueKey)?.map { (repository, commits) ->
            getVcsService(repository.type)?.let { vcsService ->
                vcsService.findCommits(
                    repository.group,
                    repository.name,
                    commits.map { commit -> commit.hash }.toSet()
                )
            } ?: emptyList()
        }?.flatten() ?: vcsServices.flatMap { it.findCommits(issueKey) }
        log.trace("<= findCommits({}): {}", issueKey, commits)
        return commits
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.trace("=> findPullRequests({})", issueKey)
        val pullRequests = openSearchService?.findPullRequestsByIssueKey(issueKey)?.map { (repository, pullRequests) ->
            getVcsService(repository.type)?.let { vcsService ->
                vcsService.findPullRequests(
                    repository.group, repository.name, pullRequests.map { pullRequest -> pullRequest.index }.toSet()
                )
            } ?: emptyList()
        }?.flatten() ?: vcsServices.flatMap { it.findPullRequests(issueKey) }
        log.trace("<= findPullRequests({}): {}", issueKey, pullRequests)
        return pullRequests
    }

    override fun find(issueKey: String): SearchSummary {
        log.trace("=> find({})", issueKey)
        val searchSummary = openSearchService?.findByIssueKey(issueKey) ?: run {
            val branchesCommits = vcsServices.flatMap { vcsService ->
                vcsService.findBranches(issueKey).groupBy { it.repository.sshUrl }.flatMap {
                    val (group, repository) = vcsService.parse(it.key)
                    vcsService.findCommits(group, repository, it.value.map { branch -> branch.commitId }.toSet())
                }
            }
            val commits = vcsServices.flatMap { it.findCommits(issueKey) }
            val pullRequests = vcsServices.flatMap { it.findPullRequests(issueKey) }
            SearchSummary(SearchSummary.SearchBranchesSummary(
                branchesCommits.size,
                branchesCommits.maxOfOrNull { it.date }),
                SearchSummary.SearchCommitsSummary(commits.size, commits.maxOfOrNull { it.date }),
                SearchSummary.SearchPullRequestsSummary(pullRequests.size,
                    pullRequests.maxOfOrNull { it.updatedAt },
                    with(pullRequests.map { it.status }.toSet()) {
                        if (size == 1) first() else null
                    })
            )
        }
        log.trace("<= find({}): {}", issueKey, searchSummary)
        return searchSummary
    }

    override fun health(): Health {
        log.trace("Run health check")
        val errors = vcsProperties.mapNotNull {
            try {
                val commits = getCommits(
                    it.healthCheck.repo, it.healthCheck.lastRelease, null, it.healthCheck.rootCommit
                ).map { commit -> commit.id }.toSet()
                val expectedCommits = it.healthCheck.expectedCommits
                if (expectedCommits != commits) {
                    val diff = (commits - expectedCommits).union(expectedCommits - commits)
                    "The symmetric difference of response commits with expected commits is $diff, repository `${it.healthCheck.repo}`".also { message ->
                        log.warn(message)
                    }
                } else null
            } catch (e: Exception) {
                "Health check request to repository `${it.healthCheck.repo}` ended with exception".also { message ->
                    log.warn(message, e)
                }
            }
        }
        val health = if (errors.isEmpty()) {
            Health.up().build()
        } else {
            Health.down().withDetail("errors", errors.joinToString(separator = ". ")).build()
        }
        log.trace("Health check status is {}", health.status)
        return health
    }

    private fun getVcsService(sshUrl: String) = vcsServices.firstOrNull { it.isSupport(sshUrl) }
        ?: throw IllegalStateException("There is no configured VCS service for `$sshUrl`")

    private fun getVcsService(vcsServiceType: VcsServiceType) =
        vcsServices.firstOrNull { it.vcsServiceType == vcsServiceType }

    companion object {
        private val log = LoggerFactory.getLogger(VCSManagerImpl::class.java)
    }
}
