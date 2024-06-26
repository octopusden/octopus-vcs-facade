package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.stream.Collectors
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsConfig
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser.validateIssueKey
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDto
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
    override fun getTags(sshUrl: String): Sequence<Tag> {
        log.trace("=> getTags({})", sshUrl)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTags(group, repository)
        }.also { log.trace("<= getTags({}): {}", sshUrl, it) }
    }

    override fun createTag(sshUrl: String, createTag: CreateTag): Tag {
        log.trace("=> createTag({}, {})", sshUrl, createTag)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            createTag(group, repository, createTag)
        }.also { log.trace("<= getTags({}, {}): {}", sshUrl, createTag, it) }
    }

    override fun getTag(sshUrl: String, name: String): Tag {
        log.trace("=> getTag({}, {})", sshUrl, name)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTag(group, repository, name)
        }.also { log.trace("<= getTag({}, {}): {}", sshUrl, name, it) }
    }

    override fun deleteTag(sshUrl: String, name: String) {
        log.trace("=> deleteTag({}, {})", sshUrl, name)
        getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            deleteTag(group, repository, name)
        }.also { log.trace("<= deleteTag({}, {}): {}", sshUrl, name, it) }
    }

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ): Sequence<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", sshUrl, fromHashOrRef, fromDate, toHashOrRef)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommits(group, repository, HashOrRefOrDate.create(fromHashOrRef, fromDate), toHashOrRef)
        }.also { log.trace("<= getCommits({}, {}, {}, {}): {}", sshUrl, fromHashOrRef, fromDate, toHashOrRef, it) }
    }

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ): Sequence<CommitWithFiles> {
        log.trace("=> getCommitsWithFiles({}, {}, {}, {})", sshUrl, fromHashOrRef, fromDate, toHashOrRef)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommitsWithFiles(group, repository, HashOrRefOrDate.create(fromHashOrRef, fromDate), toHashOrRef)
        }.also {
            log.trace("<= getCommitsWithFiles({}, {}, {}, {}): {}", sshUrl, fromHashOrRef, fromDate, toHashOrRef, it)
        }
    }

    override fun getCommit(sshUrl: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {})", sshUrl, hashOrRef)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommit(group, repository, hashOrRef)
        }.also { log.trace("<= getCommit({}, {}): {}", sshUrl, hashOrRef, it) }
    }

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String): CommitWithFiles {
        log.trace("=> getCommitWithFiles({}, {})", sshUrl, hashOrRef)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommitWithFiles(group, repository, hashOrRef)
        }.also { log.trace("<= getCommitWithFiles({}, {}): {}", sshUrl, hashOrRef, it) }
    }

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest {
        log.trace("=> createPullRequest({}, {})", sshUrl, createPullRequest)
        return getVcsService(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            createPullRequest(group, repository, createPullRequest)
        }.also { log.trace("<= createPullRequest({}, {}): {}", sshUrl, createPullRequest, it) }
    }

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse {
        log.trace("=> searchIssuesInRanges({})", searchRequest)
        searchRequest.issueKeys.map { validateIssueKey(it) }
        val messageRanges = searchRequest.ranges.flatMap { range ->
            getCommits(
                range.sshUrl, range.fromHashOrRef, range.fromDate, range.toHashOrRef
            ).map { commit ->
                commit.message to RepositoryRange(
                    commit.repository.sshUrl,
                    range.fromHashOrRef,
                    range.fromDate,
                    range.toHashOrRef
                )
            }
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
        ).also { log.trace("<= searchIssuesInRanges({}): {}", searchRequest, it) }
    }

    override fun findBranches(issueKey: String): Sequence<Branch> {
        log.trace("=> findBranches({})", issueKey)
        validateIssueKey(issueKey)
        val branches = openSearchService?.findBranchesByIssueKey(issueKey)?.map { it.toDto() as Branch }
            ?: vcsServices.flatMap { it.findBranches(issueKey) }.asSequence()
        log.trace("<= findBranches({}): {}", issueKey, branches)
        return branches
    }

    override fun findCommits(issueKey: String): Sequence<Commit> {
        log.trace("=> findCommits({})", issueKey)
        validateIssueKey(issueKey)
        val commits = openSearchService?.findCommitsByIssueKey(issueKey)?.map { it.toDto().commit }
            ?: vcsServices.flatMap { it.findCommits(issueKey) }.asSequence()
        log.trace("<= findCommits({}): {}", issueKey, commits)
        return commits
    }

    override fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles> {
        log.trace("=> findCommitsWithFiles({})", issueKey)
        validateIssueKey(issueKey)
        val commits = openSearchService?.findCommitsByIssueKey(issueKey)?.map { it.toDto() }
            ?: vcsServices.flatMap { it.findCommitsWithFiles(issueKey) }.asSequence()
        log.trace("<= findCommitsWithFiles({}): {}", issueKey, commits)
        return commits
    }

    override fun findPullRequests(issueKey: String): Sequence<PullRequest> {
        log.trace("=> findPullRequests({})", issueKey)
        validateIssueKey(issueKey)
        val pullRequests = openSearchService?.findPullRequestsByIssueKey(issueKey)?.map { it.toDto() }
            ?: vcsServices.flatMap { it.findPullRequests(issueKey) }.asSequence()
        log.trace("<= findPullRequests({}): {}", issueKey, pullRequests)
        return pullRequests
    }

    override fun find(issueKey: String): SearchSummary {
        log.trace("=> find({})", issueKey)
        validateIssueKey(issueKey)
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
                    "The symmetric difference of response commits with expected commits is $diff, repository ${it.healthCheck.repo}".also { message ->
                        log.warn(message)
                    }
                } else null
            } catch (e: Exception) {
                "Health check request to repository ${it.healthCheck.repo} ended with exception".also { message ->
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
        ?: throw IllegalStateException("There is no configured VCS service for $sshUrl")

    companion object {
        private val log = LoggerFactory.getLogger(VcsManagerImpl::class.java)
    }
}
