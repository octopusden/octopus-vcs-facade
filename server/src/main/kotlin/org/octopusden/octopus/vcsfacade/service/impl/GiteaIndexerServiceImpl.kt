package org.octopusden.octopus.vcsfacade.service.impl

import jakarta.annotation.PreDestroy
import java.util.Date
import java.util.concurrent.Executors
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaRepository
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.exception.IndexerDisabledException
import org.octopusden.octopus.vcsfacade.service.GiteaIndexerService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit as RepositoryCommit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest as RepositoryPullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Ref as RepositoryRef

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaIndexerServiceImpl(
    private val giteaService: GiteaService, private val openSearchService: OpenSearchService?
) : GiteaIndexerService { //TODO: try to use DTO from Gitea client?
    private fun getOpenSearchService() = openSearchService
        ?: throw IndexerDisabledException("VCS indexation is disabled (opensearch integration is not configured)")

    //WARNING: this is a temporary solution for test purposes
    //TODO: use AsyncTaskExecutor initialized with application properties etc.
    private val executor = Executors.newSingleThreadExecutor()

    @PreDestroy
    private fun shutdownExecutor() {
        executor.shutdownNow()
    }
    //

    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.debug(
            "Register `{}` {} creation in `{}` {} repository",
            giteaCreateRefEvent.ref,
            giteaCreateRefEvent.refType.jsonValue,
            giteaCreateRefEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        getOpenSearchService().saveRefs(listOf(giteaCreateRefEvent.toDocument()))
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.debug(
            "Register `{}` {} deletion in `{}` {} repository",
            giteaDeleteRefEvent.ref,
            giteaDeleteRefEvent.refType.jsonValue,
            giteaDeleteRefEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        getOpenSearchService().deleteRefsByIds(listOf(giteaDeleteRefEvent.toDocumentId()))
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.debug(
            "Register {} commit(s) in `{}` {} repository",
            giteaPushEvent.commits.size,
            giteaPushEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        getOpenSearchService().saveCommits(giteaPushEvent.toDocuments())
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.debug(
            "Register {} pull request in `{}` {} repository",
            giteaPullRequestEvent.action,
            giteaPullRequestEvent.repository.fullName,
            VcsServiceType.GITEA
        )
        getOpenSearchService().savePullRequests(listOf(giteaPullRequestEvent.toDocument()))
    }

    //WARNING: this is a temporary solution for test purposes
    override fun runRepositoryScan(giteaRepository: GiteaRepository) {
        executor.submit {
            try {//TODO: check if repository exists and remove it (if it's not)
                val openSearchService = getOpenSearchService()
                val repository = with(giteaRepository.toDocument()) {
                    openSearchService.findRepositoryById(this.id) ?: openSearchService.saveRepository(this)
                }
                log.debug("Scan `{}` {} repository refs", giteaRepository.fullName, VcsServiceType.GITEA)
                val refs = giteaService.getBranches(repository.group, repository.name).map { it.toDocument(repository.id) } +
                        giteaService.getTags(repository.group, repository.name).map { it.toDocument(repository.id) }
                val refsIds = refs.map { it.id }.toSet()
                openSearchService.deleteRefsByIds(openSearchService.findRefsByRepositoryId(repository.id).mapNotNull {
                    if (refsIds.contains(it.id)) null else it.id
                })
                openSearchService.saveRefs(refs)
                //TODO: bad implementation of commits scanning, it is required to take into account refs intersections, start with default branch etc.
                val commits = mutableMapOf<String, Commit>()
                refs.filter {
                    it.type == RefType.BRANCH
                }.forEach { ref ->
                    log.debug("Scan `{}` {} repository commits in {} branch", giteaRepository.fullName, VcsServiceType.GITEA, ref.name)
                    commits.putAll(giteaService.getCommits(repository.group, repository.name, ref.hash, null)
                        .map { it.toDocument(repository.id) }.associateBy { it.hash })
                }
                openSearchService.deleteCommitsByIds(openSearchService.findCommitsByRepositoryId(repository.id)
                    .mapNotNull { if (commits.containsKey(it.id)) null else it.id })
                openSearchService.saveCommits(commits.values.toList())
                //
                log.debug("Scan `{}` {} repository pull-requests", giteaRepository.fullName, VcsServiceType.GITEA)
                val pullRequests = giteaService.getPullRequests(repository.group, repository.name).map { it.toDocument(repository.id) }
                val pullRequestsIds = pullRequests.map { it.id }.toSet()
                openSearchService.deletePullRequestsByIds(openSearchService.findPullRequestsByRepositoryId(repository.id)
                    .mapNotNull { if (pullRequestsIds.contains(it.id)) null else it.id })
                openSearchService.savePullRequests(pullRequests)
                openSearchService.saveRepository(repository.apply {
                    this.lastScanAt = Date()
                })
                log.info("Scanning of `{}` {} repository completed successfully", giteaRepository.fullName, VcsServiceType.GITEA)
            } catch (e: Exception) {
                log.error("Scanning of `${giteaRepository.fullName}` ${VcsServiceType.GITEA} repository ended in failure", e)
            }
        }
    }

    private fun RepositoryRef.toDocument(repositoryId: String) = Ref(repositoryId, type, name, commitId)

    private fun RepositoryCommit.toDocument(repositoryId: String) = Commit(repositoryId, id, message, date)

    private fun RepositoryPullRequest.toDocument(repositoryId: String) = PullRequest(
        repositoryId, index, title, description, status, updatedAt
    )

    private fun registerGiteaRepository(giteaRepository: GiteaRepository): Repository {
        val openSearchService = getOpenSearchService()
        val repository = giteaRepository.toDocument()
        return openSearchService.findRepositoryById(repository.id) ?: openSearchService.saveRepository(repository)
    }

    private fun GiteaRepository.toDocument(): Repository {
        val repositoryFullNameParts = this.fullName.lowercase().split("/")
        return Repository(VcsServiceType.GITEA, repositoryFullNameParts[0], repositoryFullNameParts[1])
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
        private val log = LoggerFactory.getLogger(GiteaIndexerServiceImpl::class.java)
    }
}