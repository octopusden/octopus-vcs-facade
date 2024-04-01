package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.IndexReport
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType.GITEA
import org.octopusden.octopus.vcsfacade.exception.IndexerDisabledException
import org.octopusden.octopus.vcsfacade.service.GiteaIndexerService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.getPullRequestStatus
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toOrganizationAndRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit as RepositoryCommit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest as RepositoryPullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Ref as RepositoryRef

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaIndexerServiceImpl(
    private val giteaService: GiteaService,
    private val openSearchService: OpenSearchService?,
    @Qualifier("giteaIndexScanExecutor") private val giteaIndexScanExecutor: AsyncTaskExecutor?
) : GiteaIndexerService {
    private fun getOpenSearchService() = openSearchService
        ?: throw IndexerDisabledException("VCS indexation is disabled (opensearch integration is not configured)")

    @Scheduled(cron = "#{ @giteaIndexScanCron }")
    private fun rescan() = try {
        log.debug("Submit {} repositories rescan", GITEA)
        (giteaService.getRepositories().map { it.sshUrl.toRepository() } + getOpenSearchService().getRepositories())
            .associateBy { it.id }.forEach { submitRepositoryScan(it.value) }
    } catch (e: Exception) {
        log.error("$GITEA repositories rescan ended in failure", e)
    }

    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.trace("=> registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
        getOpenSearchService().saveRefs(listOf(giteaCreateRefEvent.toDocument()))
        log.trace("<= registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
        getOpenSearchService().deleteRefsByIds(listOf(giteaDeleteRefEvent.toDocumentId()))
        log.trace("<= registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({})", giteaPushEvent)
        getOpenSearchService().saveCommits(giteaPushEvent.toDocuments())
        log.trace("<= registerGiteaPushEvent({})", giteaPushEvent)
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
        getOpenSearchService().savePullRequests(listOf(giteaPullRequestEvent.toDocument()))
        log.trace("<= registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
    }

    override fun submitRepositoryScan(sshUrl: String) {
        log.trace("=> submitRepositoryScan({})", sshUrl)
        submitRepositoryScan(sshUrl.toRepository())
        log.trace("<= submitRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(): IndexReport {
        log.trace("=> getIndexReport()")
        return IndexReport(getOpenSearchService().getRepositories(GITEA).map { it.toIndexReportRepository() }).also {
            log.trace("=> getIndexReport(): {}", it)
        }
    }

    //TODO: configurable minimum "repository rescan not allowed/required" period after previous scan?
    private fun submitRepositoryScan(repository: Repository) {
        giteaIndexScanExecutor?.submit {
            try {
                val openSearchService = getOpenSearchService()
                if (giteaService.isRepositoryExist(repository.group, repository.name)) {
                    with(
                        openSearchService.findRepositoryById(repository.id)
                            ?: openSearchService.saveRepository(repository)
                    ) {
                        log.debug("Update `{}` {} repository refs in index", fullName, GITEA)
                        val refs = giteaService.getBranches(group, name).map { it.toDocument(id) } +
                                giteaService.getTags(group, name).map { it.toDocument(id) }
                        val refsIds = refs.map { it.id }.toSet()
                        openSearchService.deleteRefsByIds(
                            openSearchService.findRefsByRepositoryId(id).mapNotNull {
                                if (refsIds.contains(it.id)) null else it.id
                            })
                        openSearchService.saveRefs(refs)
                        log.debug("Update `{}` {} repository commits in index", fullName, GITEA)
                        val commits = giteaService.getBranchesCommitGraph(group, name).map { it.toDocument(id) }
                        val commitsIds = commits.map { it.id }.toSet()
                        openSearchService.deleteCommitsByIds(
                            openSearchService.findCommitsByRepositoryId(id).mapNotNull {
                                if (commitsIds.contains(it.id)) null else it.id
                            })
                        openSearchService.saveCommits(commits)
                        log.debug("Update `{}` {} repository pull-requests in index", fullName, GITEA)
                        val pullRequests = try {
                            giteaService.getPullRequests(group, name)
                        } catch (e: NotFoundException) {
                            emptyList() //for some reason Gitea returns 404 in case of empty repository
                        }.map { it.toDocument(id) }
                        val pullRequestsIds = pullRequests.map { it.id }.toSet()
                        openSearchService.deletePullRequestsByIds(
                            openSearchService.findPullRequestsByRepositoryId(id).mapNotNull {
                                if (pullRequestsIds.contains(it.id)) null else it.id
                            })
                        openSearchService.savePullRequests(pullRequests)
                        lastScanAt = Date()
                        openSearchService.saveRepository(this)
                    }
                } else {
                    log.debug("Remove `{}` {} repository pull-requests from index", repository.fullName, GITEA)
                    openSearchService.deletePullRequestsByRepositoryId(repository.id)
                    log.debug("Remove `{}` {} repository commits from index", repository.fullName, GITEA)
                    openSearchService.deleteCommitsByRepositoryId(repository.id)
                    log.debug("Remove `{}` {} repository refs from index", repository.fullName, GITEA)
                    openSearchService.deleteRefsByRepositoryId(repository.id)
                    openSearchService.deleteRepository(repository)
                }
                log.info("Scanning of `{}` {} repository completed successfully", repository.fullName, GITEA)
            } catch (e: Exception) {
                log.error("Scanning of `${repository.fullName}` $GITEA repository ended in failure", e)
            }
        } ?: throw IndexerDisabledException("$GITEA repository scan is disabled (scan executor is not configured)")
    }

    private fun registerGiteaRepository(giteaRepository: GiteaRepository): Repository {
        val openSearchService = getOpenSearchService()
        val repository = giteaRepository.toDocument()
        return openSearchService.findRepositoryById(repository.id) ?: openSearchService.saveRepository(repository)
    }

    private fun String.toRepository(): Repository {
        val (group, name) = giteaService.parse(this)
        return Repository(GITEA, group, name)
    }

    private fun GiteaRepository.toDocument(): Repository {
        val (organization, repository) = toOrganizationAndRepository()
        return Repository(GITEA, organization, repository)
    }

    private val Repository.fullName: String
        get() = "$group/$name"

    private fun Repository.toIndexReportRepository() = IndexReport.IndexReportRepository(
        giteaService.getSshUrl(group, name), lastScanAt
    )

    private fun RepositoryRef.toDocument(repositoryId: String) = Ref(repositoryId, type, name, commitId)

    private fun RepositoryCommit.toDocument(repositoryId: String) = Commit(repositoryId, id, message, date)

    private fun RepositoryPullRequest.toDocument(repositoryId: String) = PullRequest(
        repositoryId, index, title, description, status, updatedAt
    )

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
        pullRequest.getPullRequestStatus(),
        pullRequest.updatedAt
    )

    companion object {
        private val log = LoggerFactory.getLogger(GiteaIndexerServiceImpl::class.java)
    }
}