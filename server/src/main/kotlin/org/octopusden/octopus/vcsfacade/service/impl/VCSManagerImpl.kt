package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.stream.Collectors
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.octopusden.octopus.vcsfacade.service.VCSManager
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class VCSManagerImpl(
    private val vcsClients: List<VCSClient>,
    private val vcsProperties: List<VCSConfig.VCSProperties>,
    private val openSearchService: OpenSearchService?
) : VCSManager, HealthIndicator {
    override fun getTags(vcsPath: String): List<Tag> {
        log.debug("getTags({})", vcsPath)
        return getVcsClient(vcsPath).run {
            val (group, repository) = this.parseRepository(vcsPath)
            this.getTags(group, repository)
        }
    }

    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit> {
        log.debug("getCommits({}, {}, {}, {})", vcsPath, fromId, fromDate, toId)
        val client = getVcsClient(vcsPath)
        val (group, repository) = client.parseRepository(vcsPath)
        val started = System.currentTimeMillis()
        return if (fromId != null) {
            if (fromDate != null) {
                throw ArgumentsNotCompatibleException("Params 'fromId' and 'fromDate' can not be used together")
            }
            if (fromId == toId) {
                emptyList()
            } else {
                client.getCommits(group, repository, toId, fromId)
            }
        } else {
            client.getCommits(group, repository, toId, fromDate)
        }.also { commits ->
            log.debug(
                "Found commits: [{}] {}ms",
                commits.joinToString(", ") { commit ->
                    "${commit.id} -> ${commit.message.take(20)}"
                },
                System.currentTimeMillis() - started
            )
        }
    }

    override fun getCommit(vcsPath: String, commitIdOrRef: String): Commit {
        log.debug("getCommit({}, {})", vcsPath, commitIdOrRef)
        return getVcsClient(vcsPath).run {
            val (group, repository) = this.parseRepository(vcsPath)
            this.getCommit(group, repository, commitIdOrRef)
        }
    }

    override fun createPullRequest(vcsPath: String, createPullRequest: CreatePullRequest): PullRequest {
        log.debug("createPullRequest({}, {})", vcsPath, createPullRequest)
        return getVcsClient(vcsPath).run {
            val (group, repository) = this.parseRepository(vcsPath)
            this.createPullRequest(group, repository, createPullRequest)
        }
    }

    override fun getIssueRanges(searchRequest: SearchIssuesInRangesRequest): Map<String, Set<RepositoryRange>> {
        log.debug("getIssueRanges({})", searchRequest)
        val messageRanges = searchRequest.ranges
            .flatMap { range ->
                getCommits(range.vcsPath, range.fromCid, range.fromDate, range.toCid)
                    .map { commit -> commit.message to range }
            }
            .groupBy({ (message, _) -> message }, { (_, range) -> range })
        return searchRequest.issues
            .map { issue ->
                val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issue)
                issue to messageRanges.entries
                    .filter { (message, _) -> issueKeyRegex.containsMatchIn(message) }
                    .flatMap { (_, ranges) -> ranges }
            }.groupBy({ (issue, _) -> issue }, { (_, ranges) -> ranges })
            .entries
            .stream()
            .collect(
                Collectors.toMap({ (issue, _) -> issue },
                    { (_, ranges) -> ranges.flatten().toSet() },
                    { a, b -> a + b }
                ))
            .filter { (_, ranges) -> ranges.isNotEmpty() }
    }

    override fun findBranches(issueKey: String) =
        openSearchService?.let {
            log.debug("findBranches({}) using opensearch index", issueKey)
            it.findBranches(issueKey).map { (repository, refs) ->
                getVcsClient(repository.type)?.let { client ->
                    val branchNames = refs.map { ref -> ref.name }.toSet()
                    client.getBranches(repository.group, repository.name).filter { branch ->
                        branch.name in branchNames
                    }
                } ?: emptyList()
            }.flatten()
        } ?: run {
            log.debug("findBranches({}) using native implementation", issueKey)
            vcsClients.flatMap { it.findBranches(issueKey) }
        }

    override fun findCommits(issueKey: String) =
        openSearchService?.let {
            log.debug("findCommits({}) using opensearch index", issueKey)
            it.findCommits(issueKey).map { (repository, commits) ->
                getVcsClient(repository.type)?.let { client ->
                    commits.mapNotNull { commit ->
                        try {
                            client.getCommit(repository.group, repository.name, commit.hash)
                        } catch (e: NotFoundException) {
                            null
                        }
                    }
                } ?: emptyList()
            }.flatten()
        } ?: run {
            log.debug("findCommits({}) using native implementation", issueKey)
            vcsClients.flatMap { it.findCommits(issueKey) }
        }

    override fun findPullRequests(issueKey: String) =
        openSearchService?.let {
            log.debug("findPullRequests({}) using opensearch index", issueKey)
            it.findPullRequests(issueKey).map { (repository, pullRequests) ->
                getVcsClient(repository.type)?.let { client ->
                    pullRequests.mapNotNull { pullRequest ->
                        try {
                            client.getPullRequest(repository.group, repository.name, pullRequest.index)
                        } catch (e: NotFoundException) {
                            null
                        }
                    }
                } ?: emptyList()
            }.flatten()
        } ?: run {
            log.debug("findPullRequests({}) using native implementation", issueKey)
            vcsClients.flatMap { it.findPullRequests(issueKey) }
        }

    override fun find(issueKey: String) =
        openSearchService?.let {
            log.debug("find({}) using opensearch index", issueKey)
            it.find(issueKey) //IMPORTANT: no "double check in VCS" for the sake of the performance
        } ?: run {
            log.debug("find({}) using native implementation", issueKey)
            val branchesCommits = vcsClients.flatMap {
                it.findBranches(issueKey).map { branch ->
                    val (group, repository) = it.parseRepository(branch.vcsUrl)
                    it.getCommit(group, repository, branch.commitId)
                }
            }
            val commits = vcsClients.flatMap { it.findCommits(issueKey) }
            val pullRequests = vcsClients.flatMap { it.findPullRequests(issueKey) }
            SearchSummary(
                SearchSummary.SearchBranchesSummary(
                    branchesCommits.size,
                    branchesCommits.maxOfOrNull { it.date }
                ),
                SearchSummary.SearchCommitsSummary(
                    commits.size,
                    commits.maxOfOrNull { it.date }
                ),
                SearchSummary.SearchPullRequestsSummary(
                    pullRequests.size,
                    pullRequests.maxOfOrNull { it.updatedAt },
                    with(pullRequests.map { it.status }.toSet()) {
                        if (this.size == 1) this.first() else null
                    })
            )
        }

    override fun health(): Health {
        return vcsProperties
            .filter { props -> props.enabled }
            .map { provider ->
                try {
                    val commits = getCommits(
                        provider.healthCheck.repo,
                        provider.healthCheck.lastRelease,
                        null,
                        provider.healthCheck.rootCommit
                    )
                        .map { it.id }
                        .toSet()
                    val expectedCommits = provider.healthCheck.expectedCommits
                    if (expectedCommits == commits) {
                        true to null
                    } else {
                        val diff = (commits - expectedCommits).union(expectedCommits - commits)
                        false to "The symmetric difference of response commits with expected commits is: $diff, repository: '${provider.host}'"
                    }
                } catch (e: Exception) {
                    false to "Request to repository: '${provider.host}' ended with exception: ${e.message}"
                }
            }
            .filter { !it.first }
            .map { it.second }
            .let {
                if (it.isNotEmpty()) {
                    Health.down().withDetail("errors", it.joinToString(separator = ". ")).build()
                } else {
                    Health.up().build()
                }
            }
    }

    private fun getVcsClient(vcsPath: String) = vcsClients.firstOrNull { it.isSupport(vcsPath) }
        ?: throw IllegalStateException("There is no enabled or/and supported VCS client for vcsPath=$vcsPath")

    private fun getVcsClient(vcsServiceType: VcsServiceType) =
        vcsClients.firstOrNull { it.vcsServiceType == vcsServiceType }

    companion object {
        private val log = LoggerFactory.getLogger(VCSManagerImpl::class.java)
    }
}
