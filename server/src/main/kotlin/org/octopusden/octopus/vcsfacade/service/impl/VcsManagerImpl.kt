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
import org.octopusden.octopus.vcsfacade.config.VcsConfig
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class VcsManagerImpl(
    private val vcsServices: List<VcsService>,
    private val vcsProperties: List<VcsConfig.VcsProperties>,
    private val openSearchService: OpenSearchService?
) : VcsManager, HealthIndicator {
    override fun getTags(sshUrl: String): List<Tag> {
        log.trace("=> getTags({})", sshUrl)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTags(group, repository)
        }.also {
            log.trace("<= getTags({}): {}", sshUrl, it)
        }
    }

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", sshUrl, fromHashOrRef, fromDate, toHashOrRef)
        val vcsService = getVcsService(sshUrl)
        val (group, repository) = vcsService.parse(sshUrl)
        val commits = if (fromHashOrRef != null) {
            if (fromDate != null) {
                throw ArgumentsNotCompatibleException("Params 'fromHashOrRef' and 'fromDate' can not be used together")
            }
            if (fromHashOrRef == toHashOrRef) {
                emptyList()
            } else {
                vcsService.getCommits(group, repository, toHashOrRef, fromHashOrRef)
            }
        } else {
            vcsService.getCommits(group, repository, toHashOrRef, fromDate)
        }
        log.trace("<= getCommits({}, {}, {}, {}): {}", sshUrl, fromHashOrRef, fromDate, toHashOrRef, commits)
        return commits
    }

    override fun getCommit(sshUrl: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {})", sshUrl, hashOrRef)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommit(group, repository, hashOrRef)
        }.also {
            log.trace("<= getCommit({}, {}): {}", sshUrl, hashOrRef, it)
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
                range.sshUrl, range.fromHashOrRef, range.fromDate, range.toHashOrRef
            ).map { commit -> commit.message to range }
        }.groupBy({ (message, _) -> message }, { (_, range) -> range })
        return SearchIssueInRangesResponse(searchRequest.issueKeys.map { issueKey ->
            val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
            issueKey to messageRanges.entries.filter { (message, _) -> issueKeyRegex.containsMatchIn(message) }
                .flatMap { (_, ranges) -> ranges }
        }.groupBy({ (issueKey, _) -> issueKey }, { (_, ranges) -> ranges }).entries.stream()
            .collect(
                Collectors.toMap({ (issueKey, _) -> issueKey },
                    { (_, ranges) -> ranges.flatten().toSet() },
                    { a, b -> a + b })
            ).filter { (_, ranges) -> ranges.isNotEmpty() }
        ).also {
            log.trace("<= searchIssuesInRanges({}): {}", searchRequest, it)
        }
    }

    override fun findBranches(issueKey: String): List<Branch> {
        log.trace("=> findBranches({})", issueKey)
        val branches = openSearchService?.findBranchesByIssueKey(issueKey)
            ?: vcsServices.flatMap { it.findBranches(issueKey) }
        log.trace("<= findBranches({}): {}", issueKey, branches)
        return branches
    }

    override fun findCommits(issueKey: String): List<Commit> {
        log.trace("=> findCommits({})", issueKey)
        val commits = openSearchService?.findCommitsByIssueKey(issueKey)
            ?: vcsServices.flatMap { it.findCommits(issueKey) }
        log.trace("<= findCommits({}): {}", issueKey, commits)
        return commits
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.trace("=> findPullRequests({})", issueKey)
        val pullRequests = openSearchService?.findPullRequestsByIssueKey(issueKey)
            ?: vcsServices.flatMap { it.findPullRequests(issueKey) }
        log.trace("<= findPullRequests({}): {}", issueKey, pullRequests)
        return pullRequests
    }

    override fun find(issueKey: String): SearchSummary {
        log.trace("=> find({})", issueKey)
        val searchSummary = openSearchService?.findByIssueKey(issueKey) ?: run {
            val branchesCommits = vcsServices.flatMap { vcsService ->
                vcsService.findBranches(issueKey).groupBy { it.repository.sshUrl }.flatMap {
                    val (group, repository) = vcsService.parse(it.key)
                    vcsService.findCommits(group, repository, it.value.map { branch -> branch.hash }.toSet())
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
                ).map { commit -> commit.hash }.toSet()
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

    companion object {
        private val log = LoggerFactory.getLogger(VcsManagerImpl::class.java)
    }
}
