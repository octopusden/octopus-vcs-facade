package org.octopusden.octopus.vcsfacade.service.impl

import kotlin.jvm.optionals.getOrNull
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.document.RepositoryLink
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaRepository
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
) : OpenSearchService { //TODO: try to use DTO from Gitea client?
    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.debug(
            "Register `{}` {} creation in `{}` {} repository",
            giteaCreateRefEvent.ref,
            giteaCreateRefEvent.refType.jsonValue,
            giteaCreateRefEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        refRepository.save(giteaCreateRefEvent.toDocument())
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.debug(
            "Register `{}` {} deletion in `{}` {} repository",
            giteaDeleteRefEvent.ref,
            giteaDeleteRefEvent.refType.jsonValue,
            giteaDeleteRefEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        refRepository.deleteById(giteaDeleteRefEvent.toDocumentId())
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.debug(
            "Register {} commit(s) in `{}` {} repository",
            giteaPushEvent.commits.size,
            giteaPushEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        commitRepository.saveAll(giteaPushEvent.toDocuments())
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.debug(
            "Register {} pull request in `{}` {} repository",
            giteaPullRequestEvent.action,
            giteaPullRequestEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        pullRequestRepository.save(giteaPullRequestEvent.toDocument())
    }

    override fun findBranches(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        refRepository.findAllByTypeAndNameContaining(RefType.BRANCH, issueKey)
            .filter { this.containsMatchIn(it.name) }
            .groupByRepository()
    }

    override fun findCommits(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        commitRepository.findAllByMessageContaining(issueKey)
            .filter { this.containsMatchIn(it.message) }
            .groupByRepository()
    }

    override fun findPullRequests(issueKey: String) = with(IssueKeyParser.getIssueKeyRegex(issueKey)) {
        pullRequestRepository.findAllByTitleContainingOrDescriptionContaining(issueKey, issueKey)
            .filter { this.containsMatchIn(it.title) || this.containsMatchIn(it.description) }
            .groupByRepository()
    }

    override fun find(issueKey: String): SearchSummary {
        val issueKeyRegex = IssueKeyParser.getIssueKeyRegex(issueKey)
        val branchesCommits = refRepository.findAllByTypeAndNameContaining(RefType.BRANCH, issueKey).filter {
            issueKeyRegex.containsMatchIn(it.name)
        }.map {
            //TODO: use commitRepository.findByRepositoryIdAndHash(it.repositoryId, it.hash).firstOrNull() instead?
            commitRepository.findById((object : RepositoryLink(it.repositoryId) {}).id(it.hash)).getOrNull()
        }
        val commits = commitRepository.findAllByMessageContaining(issueKey).filter {
            issueKeyRegex.containsMatchIn(it.message)
        }
        val pullRequests =
            pullRequestRepository.findAllByTitleContainingOrDescriptionContaining(issueKey, issueKey).filter {
                issueKeyRegex.containsMatchIn(it.title) || issueKeyRegex.containsMatchIn(it.description)
            }
        return SearchSummary(
            SearchSummary.SearchBranchesSummary(
                branchesCommits.size,
                branchesCommits.filterNotNull().maxOfOrNull { it.date }
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

    private fun <T : RepositoryLink> List<T>.groupByRepository() = this.groupBy {
        it.repositoryId
    }.mapKeys { (repositoryId, _) ->
        repositoryRepository.findById(repositoryId)
    }.filterKeys { repository ->
        repository.isPresent
    }.mapKeys { (repository, _) ->
        repository.get()
    }

    private fun registerGiteaRepository(giteaRepository: GiteaRepository): Repository {
        val repositoryFullNameParts = giteaRepository.fullName.split("/")
        val repository = Repository(VcsServiceType.GITEA, repositoryFullNameParts[0], repositoryFullNameParts[1])
        return repositoryRepository.findById(repository.id).orElseGet {
            repositoryRepository.save(repository)
        }
    }

    private fun GiteaCreateRefEvent.toDocument() =
        Ref(registerGiteaRepository(repository).id, refType.refType, ref, sha)

    private fun GiteaDeleteRefEvent.toDocumentId() =
        Ref(registerGiteaRepository(repository).id, refType.refType, ref, "unknown").id

    private fun GiteaPushEvent.toDocuments(): List<Commit> {
        val repository = registerGiteaRepository(repository)
        return commits.map { Commit(repository.id, it.id, it.message, it.timestamp) }
    }

    private fun GiteaPullRequestEvent.toDocument() = PullRequest(
        registerGiteaRepository(repository).id,
        pullRequest.number,
        pullRequest.title,
        pullRequest.body,
        if (pullRequest.merged) PullRequestStatus.MERGED
        else if (pullRequest.state == GiteaPullRequestEvent.GiteaPullRequestState.CLOSED) PullRequestStatus.DECLINED
        else PullRequestStatus.OPENED,
        pullRequest.updatedAt
    )

    companion object {
        private val log = LoggerFactory.getLogger(OpenSearchServiceImpl::class.java)
    }
}