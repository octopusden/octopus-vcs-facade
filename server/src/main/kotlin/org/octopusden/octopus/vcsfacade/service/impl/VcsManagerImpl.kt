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
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser.validateIssueKeys
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDto
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class VcsManagerImpl(
    private val vcsProperties: VcsProperties,
    private val openSearchService: OpenSearchService?,
) : VcsManager, HealthIndicator {
    private val vcsServiceMap = vcsProperties.services.map {
        when (it.type) {
            VcsServiceType.BITBUCKET -> BitbucketService(it)
            VcsServiceType.GITEA -> GiteaService(it)
        }
    }.groupBy { it.id }.mapValues {
        if (it.value.size > 1) throw IllegalStateException("${it.value.size} VCS services have similar id '${it.key}'")
        else it.value.first()
    }

    override val vcsServices = vcsServiceMap.values

    override fun findVcsServiceById(id: String) = vcsServiceMap[id.lowercase()]

    override fun getVcsServiceById(id: String) = findVcsServiceById(id)
        ?: throw IllegalStateException("There is no configured VCS service with id '$id'")

    override fun getVcsServiceForSshUrl(sshUrl: String) = vcsServices.firstOrNull { it.isSupported(sshUrl) }
        ?: throw IllegalStateException("There is no configured VCS service for '$sshUrl'")

    override fun getTags(sshUrl: String): Sequence<Tag> {
        log.trace("=> getTags({})", sshUrl)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTags(group, repository)
        }.also { if (log.isTraceEnabled) log.trace("<= getTags({}): {}", sshUrl, it.toList()) }
    }

    override fun createTag(sshUrl: String, createTag: CreateTag): Tag {
        log.trace("=> createTag({}, {})", sshUrl, createTag)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            createTag(group, repository, createTag)
        }.also { log.trace("<= getTags({}, {}): {}", sshUrl, createTag, it) }
    }

    override fun getTag(sshUrl: String, name: String): Tag {
        log.trace("=> getTag({}, {})", sshUrl, name)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getTag(group, repository, name)
        }.also { log.trace("<= getTag({}, {}): {}", sshUrl, name, it) }
    }

    override fun deleteTag(sshUrl: String, name: String) {
        log.trace("=> deleteTag({}, {})", sshUrl, name)
        getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            deleteTag(group, repository, name)
        }.also { log.trace("<= deleteTag({}, {})", sshUrl, name) }
    }

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ): Sequence<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", sshUrl, fromHashOrRef, fromDate, toHashOrRef)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommits(group, repository, HashOrRefOrDate.create(fromHashOrRef, fromDate), toHashOrRef)
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= getCommits({}, {}, {}, {}): {}",
                sshUrl,
                fromHashOrRef,
                fromDate,
                toHashOrRef,
                it.toList()
            )
        }
    }

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ): Sequence<CommitWithFiles> {
        log.trace("=> getCommitsWithFiles({}, {}, {}, {})", sshUrl, fromHashOrRef, fromDate, toHashOrRef)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommitsWithFiles(group, repository, HashOrRefOrDate.create(fromHashOrRef, fromDate), toHashOrRef)
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= getCommitsWithFiles({}, {}, {}, {}): {}",
                sshUrl,
                fromHashOrRef,
                fromDate,
                toHashOrRef,
                it.toList()
            )
        }
    }

    override fun getCommit(sshUrl: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {})", sshUrl, hashOrRef)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommit(group, repository, hashOrRef)
        }.also { log.trace("<= getCommit({}, {}): {}", sshUrl, hashOrRef, it) }
    }

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String): CommitWithFiles {
        log.trace("=> getCommitWithFiles({}, {})", sshUrl, hashOrRef)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            getCommitWithFiles(group, repository, hashOrRef)
        }.also { log.trace("<= getCommitWithFiles({}, {}): {}", sshUrl, hashOrRef, it) }
    }

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest {
        log.trace("=> createPullRequest({}, {})", sshUrl, createPullRequest)
        return getVcsServiceForSshUrl(sshUrl).run {
            val (group, repository) = parse(sshUrl)
            createPullRequest(group, repository, createPullRequest)
        }.also { log.trace("<= createPullRequest({}, {}): {}", sshUrl, createPullRequest, it) }
    }

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse {
        log.trace("=> searchIssuesInRanges({})", searchRequest)
        validateIssueKeys(searchRequest.issueKeys)
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

    override fun findBranches(issueKeys: Set<String>): Sequence<Branch> {
        log.trace("=> findBranches({})", issueKeys)
        validateIssueKeys(issueKeys)
        val branches = openSearchService?.findBranchesByIssueKeys(issueKeys)?.asSequence()?.map { it.toDto() as Branch }
            ?: issueKeys.flatMap { issueKey -> vcsServices.flatMap { it.findBranches(issueKey) } }
                .asSequence().distinctBy { it.repository.sshUrl + it.name }
        if (log.isTraceEnabled) log.trace("<= findBranches({}): {}", issueKeys, branches.toList())
        return branches
    }

    override fun findCommits(issueKeys: Set<String>): Sequence<Commit> {
        log.trace("=> findCommits({})", issueKeys)
        validateIssueKeys(issueKeys)
        val commits = openSearchService?.findCommitsByIssueKeys(issueKeys)?.asSequence()?.map { it.toDto().commit }
            ?: issueKeys.flatMap { issueKey -> vcsServices.flatMap { it.findCommits(issueKey) } }
                .asSequence().distinctBy { it.repository.sshUrl + it.hash }
        if (log.isTraceEnabled) log.trace("<= findCommits({}): {}", issueKeys, commits.toList())
        return commits
    }

    override fun findCommitsWithFiles(issueKeys: Set<String>): Sequence<CommitWithFiles> {
        log.trace("=> findCommitsWithFiles({})", issueKeys)
        validateIssueKeys(issueKeys)
        val commitsWithFiles = openSearchService?.findCommitsByIssueKeys(issueKeys)?.asSequence()?.map { it.toDto() }
            ?: issueKeys.flatMap { issueKey -> vcsServices.flatMap { it.findCommitsWithFiles(issueKey) } }
                .asSequence().distinctBy { it.commit.repository.sshUrl + it.commit.hash }
        if (log.isTraceEnabled) log.trace("<= findCommitsWithFiles({}): {}", issueKeys, commitsWithFiles.toList())
        return commitsWithFiles
    }

    override fun findPullRequests(issueKeys: Set<String>): Sequence<PullRequest> {
        log.trace("=> findPullRequests({})", issueKeys)
        validateIssueKeys(issueKeys)
        val pullRequests = openSearchService?.findPullRequestsByIssueKeys(issueKeys)?.asSequence()?.map { it.toDto() }
            ?: issueKeys.flatMap { issueKey -> vcsServices.flatMap { it.findPullRequests(issueKey) } }
                .asSequence().distinctBy { it.repository.sshUrl + it.index }
        if (log.isTraceEnabled) log.trace("<= findPullRequests({}): {}", issueKeys, pullRequests.toList())
        return pullRequests
    }

    override fun find(issueKeys: Set<String>): SearchSummary {
        log.trace("=> find({})", issueKeys)
        validateIssueKeys(issueKeys)
        val searchSummary = openSearchService?.findByIssueKeys(issueKeys) ?: run {
            val branchesCommits = issueKeys.flatMap { issueKey ->
                vcsServices.flatMap { vcsService ->
                    vcsService.findBranches(issueKey).groupBy { it.repository.sshUrl }.flatMap {
                        val (group, repository) = vcsService.parse(it.key)
                        vcsService.findCommits(group, repository, it.value.map { branch -> branch.hash }.toSet())
                    }
                }
            }.distinctBy { it.repository.sshUrl + it.hash }
            val commits = issueKeys.flatMap { issueKey ->
                vcsServices.flatMap { it.findCommits(issueKey) }
            }.distinctBy { it.repository.sshUrl + it.hash }
            val pullRequests = issueKeys.flatMap { issueKey ->
                vcsServices.flatMap { it.findPullRequests(issueKey) }
            }.distinctBy { it.repository.sshUrl + it.index }
            SearchSummary(
                SearchSummary.SearchBranchesSummary(
                    branchesCommits.size,
                    branchesCommits.maxOfOrNull { it.date }),
                SearchSummary.SearchCommitsSummary(
                    commits.size,
                    commits.maxOfOrNull { it.date }),
                SearchSummary.SearchPullRequestsSummary(
                    pullRequests.size,
                    pullRequests.maxOfOrNull { it.updatedAt },
                    with(pullRequests.map { it.status }.toSet()) {
                        if (size == 1) first() else null
                    })
            )
        }
        log.trace("<= find({}): {}", issueKeys, searchSummary)
        return searchSummary
    }

    override fun health(): Health {
        log.trace("Run health check")

        val healthCheckList = vcsProperties.services.mapNotNull { it.healthCheck ?.let { hc -> it.id to hc } }
        if (healthCheckList.isEmpty()) {
            val msg = "Health check is not configured for any VCS service"
            log.warn(msg)
            return Health.unknown().withDetail("services", msg).build()
        }

        val errors = healthCheckList.mapNotNull {
            val healthCheck = it.second
            try {
                val commits = getVcsServiceById(it.first).getCommits(
                    healthCheck.group,
                    healthCheck.repository,
                    HashOrRefOrDate.create(healthCheck.fromCommit, null),
                    healthCheck.toCommit
                ).map { commit -> commit.hash }.toSet()
                if (commits != healthCheck.expectedCommits) {
                    val diffCommits = (commits - healthCheck.expectedCommits)
                        .union(healthCheck.expectedCommits - commits)
                    "The symmetric difference of response commits with expected commits is $diffCommits"
                } else {
                    null
                }
            } catch (e: Exception) {
                log.error("Health check for VCS service with id '${it.first}' has failed", e)
                e.javaClass.name + ": " + e.message
            }
        }
        return if (errors.isEmpty()) {
            log.trace("Health check status is UP")
            Health.up().build()
        } else {
            val error = errors.joinToString(". ")
            log.error("Health check status is DOWN: $error")
            Health.down().withDetail("errors", error).build()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VcsManagerImpl::class.java)
    }
}
