package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.stream.Collectors
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.octopusden.octopus.vcsfacade.service.VCSManager
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Service

@Service
class VCSManagerImpl(
    private val vcsClients: List<VCSClient>,
    private val vcsProperties: List<VCSConfig.VCSProperties>
) : VCSManager, HealthIndicator {

    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit> {
        log.debug("getCommits($vcsPath, $fromId, $fromDate, $toId)")
        val started = System.currentTimeMillis()
        return if (fromId != null) {
            if (fromDate != null) {
                throw ArgumentsNotCompatibleException("Params 'fromId' and 'fromDate' can not be used together")
            }
            if (fromId == toId) {
                emptyList()
            } else {
                getVcsClient(vcsPath).getCommits(vcsPath, toId, fromId)
            }
        } else {
            getVcsClient(vcsPath).getCommits(vcsPath, toId, fromDate)
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

    override fun findCommits(issueKey: String): List<Commit> {
        log.debug("findCommits($issueKey)")
        return vcsClients.flatMap { it.getCommits(issueKey) }
    }

    override fun getTagsForRepository(vcsPath: String): List<Tag> {
        log.debug("getTagsForRepository($vcsPath)")
        return getVcsClient(vcsPath).getTags(vcsPath)
    }

    override fun findCommit(vcsPath: String, commitIdOrRef: String): Commit {
        log.debug("findCommit($vcsPath, $commitIdOrRef)")
        return getVcsClient(vcsPath).getCommit(vcsPath, commitIdOrRef)
    }

    override fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse {
        log.debug("createPullRequest($vcsPath, $pullRequestRequest)")
        return getVcsClient(vcsPath).createPullRequest(vcsPath, pullRequestRequest)
    }

    override fun getIssueRanges(searchRequest: SearchIssuesInRangesRequest): Map<String, Set<RepositoryRange>> {
        log.debug("getIssueRanges($searchRequest)")
        val messageRanges = searchRequest.ranges
            .flatMap { range ->
                getCommits(range.vcsPath, range.fromCid, range.fromDate, range.toCid)
                    .map { commit -> commit.message to range }
            }
            .groupBy({ (message, _) -> message }, { (_, range) -> range })
        return searchRequest.issues
            .map { issue ->
                issue to messageRanges.entries
                    .filter { (message, _) -> message.matches(issue.toIssueRegex()) }
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

    companion object {
        private val log = LoggerFactory.getLogger(VCSManagerImpl::class.java)

        fun String.toIssueRegex() = "^(.*[^a-zA-Z0-9])*$this([^a-zA-Z0-9].*)*\$".toRegex()
    }
}
