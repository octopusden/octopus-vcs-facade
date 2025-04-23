package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.concurrent.Future
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.vcsfacade.client.common.dto.IndexReport
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.IndexerService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDocument
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDto
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toBranch
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toPullRequest
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toTag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class IndexerServiceImpl(
    private val vcsManager: VcsManager,
    private val openSearchService: OpenSearchService,
    @Qualifier("opensearchIndexScanExecutor") private val opensearchIndexScanExecutor: AsyncTaskExecutor,
    @Qualifier("isMaster") private val isMaster: Boolean
) : IndexerService {
    private fun getIndexingGiteaService(id: String) = vcsManager.findVcsServiceById(id)?.let {
        if (it.indexing && it.type == VcsServiceType.GITEA) it as GiteaService
        else null
    } ?: throw IllegalStateException("There is no configured Gitea service with id '$id' and enabled indexing")

    override fun registerGiteaCreateRefEvent(vcsServiceId: String, createRefEvent: GiteaCreateRefEvent) {
        log.trace("=> registerGiteaCreateRefEvent({}, {})", vcsServiceId, createRefEvent)
        val giteaService = getIndexingGiteaService(vcsServiceId)
        val repositoryDocument = giteaService.toRepository(createRefEvent.repository).toDocument(giteaService)
        checkInRepository(repositoryDocument)
        val refDocument = when (createRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(
                createRefEvent.ref, GiteaBranch.PayloadCommit(createRefEvent.sha)
            ).toBranch(repositoryDocument.toDto()).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(createRefEvent.ref, GiteaShortCommit(createRefEvent.sha)).toTag(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)
        }
        openSearchService.saveRefs(sequenceOf(refDocument))
        log.trace("<= registerGiteaCreateRefEvent({}, {})", vcsServiceId, createRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(vcsServiceId: String, deleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({}, {})", vcsServiceId, deleteRefEvent)
        val giteaService = getIndexingGiteaService(vcsServiceId)
        val repositoryDocument = giteaService.toRepository(deleteRefEvent.repository).toDocument(giteaService)
        checkInRepository(repositoryDocument, deleteRefEvent.refType.refType == RefType.BRANCH)
        val refDocumentId = when (deleteRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(deleteRefEvent.ref, GiteaBranch.PayloadCommit("unknown")).toBranch(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(
                deleteRefEvent.ref, GiteaShortCommit("unknown")
            ).toTag(repositoryDocument.toDto()).toDocument(repositoryDocument)
        }.id
        openSearchService.deleteRefsByIds(setOf(refDocumentId))
        log.trace("<= registerGiteaDeleteRefEvent({}, {})", vcsServiceId, deleteRefEvent)
    }

    override fun registerGiteaPushEvent(vcsServiceId: String, pushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({}, {})", vcsServiceId, pushEvent)
        val giteaService = getIndexingGiteaService(vcsServiceId)
        val repositoryDocument = giteaService.toRepository(pushEvent.repository).toDocument(giteaService)
        checkInRepository(repositoryDocument)
        openSearchService.saveCommits(pushEvent.commits.asSequence().map {
            //IMPORTANT: commits in push event does not contain all demanded data, so it is required to get it from gitea directly
            giteaService.getCommitWithFiles(repositoryDocument.group, repositoryDocument.name, it.id)
                .toDocument(repositoryDocument)
        })
        log.trace("<= registerGiteaPushEvent({}, {})", vcsServiceId, pushEvent)
    }

    override fun registerGiteaPullRequestEvent(vcsServiceId: String, pullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({}, {})", vcsServiceId, pullRequestEvent)
        val giteaService = getIndexingGiteaService(vcsServiceId)
        val repositoryDocument = giteaService.toRepository(pullRequestEvent.repository).toDocument(giteaService)
        checkInRepository(repositoryDocument)
        val pullRequestDocument = pullRequestEvent.pullRequest.toPullRequest(
            repositoryDocument.toDto(),
            //IMPORTANT: to calculate reviewers approves it is required to get pull request reviews from gitea directly
            giteaService.getPullRequestReviews(
                repositoryDocument.group, repositoryDocument.name, pullRequestEvent.pullRequest.number
            )
        ).toDocument(repositoryDocument)
        openSearchService.savePullRequests(sequenceOf(pullRequestDocument))
        log.trace("<= registerGiteaPullRequestEvent({}, {})", vcsServiceId, pullRequestEvent)
    }

    override fun scheduleRepositoryScan(sshUrl: String) {
        log.trace("=> scheduleRepositoryScan({})", sshUrl)
        checkInRepository(
            Repository(sshUrl.lowercase(), "undefined").toDocument(vcsManager.getVcsServiceForSshUrl(sshUrl)),
            true
        )
        log.trace("<= scheduleRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(scanRequired: Boolean?): IndexReport {
        log.trace("=> getIndexReport(scanRequired=$scanRequired)")

        val infos = openSearchService.getRepositoriesInfo()
            .filter { scanRequired == null || it.scanRequired == scanRequired }

        return IndexReport(
            infos.map {
                IndexReport.IndexReportRepository(
                    sshUrl = it.repository.sshUrl,
                    scanRequired = it.scanRequired,
                    lastScanAt = it.lastScanAt
                )
            }
        ).also { log.trace("<= getIndexReport(): {}", it) }
    }

    private fun Repository.toDocument(vcsService: VcsService): RepositoryDocument {
        val (group, repository) = vcsService.parse(sshUrl)
        return RepositoryDocument(vcsService.id, group, repository, sshUrl, link, avatar)
    }

    private fun checkInRepository(repositoryDocument: RepositoryDocument, rescan: Boolean = false) {
        openSearchService.findRepositoryInfoById(repositoryDocument.id)?.let {
            if (rescan) {
                openSearchService.saveRepositoriesInfo(sequenceOf(it.apply { scanRequired = true }))
            }
        } ?: openSearchService.saveRepositoriesInfo(sequenceOf(RepositoryInfoDocument(repositoryDocument)))
    }

    @Scheduled(cron = "#{ @opensearchIndexScheduleRepositoriesRescanCron }")
    private fun scheduleRepositoriesRescan() = if (isMaster) {
        try {
            val repositoriesInfo = openSearchService.getRepositoriesInfo().map {
                it.apply { scanRequired = true }
            }.toSet() + vcsManager.vcsServices.filter { it.indexing }.flatMap { vcsService ->
                vcsService.getRepositories().map { RepositoryInfoDocument(it.toDocument(vcsService)) }
            }
            if (log.isTraceEnabled) {
                log.trace("Scheduled {} repositories for scan: {}", repositoriesInfo.size, repositoriesInfo)
            } else {
                log.debug("Scheduled {} repositories for scan", repositoriesInfo.size)
            }
            openSearchService.saveRepositoriesInfo(repositoriesInfo.asSequence())
        } catch (e: Exception) {
            log.error("Unable to schedule repositories for rescan", e)
        }
    } else {
        log.trace("Suppress repositories rescan scheduling on non-master instance")
    }

    private val repositoryScanQueue = mutableMapOf<RepositoryInfoDocument, Future<*>>()

    @Scheduled(fixedDelayString = "#{ @opensearchIndexSubmitScheduledRepositoriesScanFixedDelay }")
    private fun submitScheduledRepositoriesScan() = if (isMaster) {
        try {
            repositoryScanQueue.filterValues { it.isDone }.keys.forEach {
                repositoryScanQueue.remove(it)
            }
            openSearchService.getRepositoriesInfo().filter { it.scanRequired }.forEach {
                repositoryScanQueue.computeIfAbsent(it) { repositoryInfoDocument ->
                    opensearchIndexScanExecutor.submit { scan(repositoryInfoDocument.repository) }
                }
            }
            if (log.isTraceEnabled) {
                log.trace("{} repositories in scan queue: {}", repositoryScanQueue.size, repositoryScanQueue.keys)
            } else {
                log.debug("{} repositories in scan queue", repositoryScanQueue.size)
            }
        } catch (e: Exception) {
            log.error("Unable to submit repositories for scan", e)
        }
    } else {
        log.trace("Suppress repositories scan submitting on non-master instance")
    }

    private fun scan(repositoryDocument: RepositoryDocument) = try {
        val vcsService = vcsManager.findVcsServiceById(repositoryDocument.vcsServiceId)
        val foundRepositoryDocument = vcsService?.let {
            if (it.indexing) it.findRepository(repositoryDocument.group, repositoryDocument.name)?.toDocument(it)
            else null
        }
        if (foundRepositoryDocument == repositoryDocument) { //IMPORTANT: found repository could be renamed one
            with(RepositoryInfoDocument(foundRepositoryDocument, false, Date())) {
                log.debug("Update {} repository info in index", repository.sshUrl)
                openSearchService.saveRepositoriesInfo(sequenceOf(this))
                log.debug("Update {} repository refs in index", repository.sshUrl)
                val foundRefsIds = openSearchService.findRefsIdsByRepositoryId(repository.id)
                val savedBranchesIds = openSearchService.saveRefs(
                    vcsService.getBranches(repository.group, repository.name).map { it.toDocument(repository) }
                )
                val savedTagsIds = openSearchService.saveRefs(
                    vcsService.getTags(repository.group, repository.name).map { it.toDocument(repository) }
                )
                openSearchService.deleteRefsByIds(foundRefsIds - savedBranchesIds - savedTagsIds)
                log.debug("Update {} repository commits in index", repository.sshUrl)
                val foundCommitsIds = openSearchService.findCommitsIdsByRepositoryId(repository.id)
                val savedCommitsIds = openSearchService.saveCommits(
                    vcsService.getBranchesCommitGraph(repository.group, repository.name)
                        .map { it.toDocument(repository) }
                )
                openSearchService.deleteCommitsByIds(foundCommitsIds - savedCommitsIds)
                log.debug("Update {} repository pull requests in index", repository.sshUrl)
                val foundPullRequestsIds = openSearchService.findPullRequestsIdsByRepositoryId(repository.id)
                val savedPullRequestsIds = openSearchService.savePullRequests(
                    vcsService.getPullRequests(repository.group, repository.name).map { it.toDocument(repository) }
                )
                openSearchService.deletePullRequestsByIds(foundPullRequestsIds - savedPullRequestsIds)
            }
        } else {
            log.debug("Remove {} repository pull-requests from index", repositoryDocument.sshUrl)
            openSearchService.deletePullRequestsByRepositoryId(repositoryDocument.id)
            log.debug("Remove {} repository commits from index", repositoryDocument.sshUrl)
            openSearchService.deleteCommitsByRepositoryId(repositoryDocument.id)
            log.debug("Remove {} repository refs from index", repositoryDocument.sshUrl)
            openSearchService.deleteRefsByRepositoryId(repositoryDocument.id)
            log.debug("Remove {} repository info from index", repositoryDocument.sshUrl)
            openSearchService.deleteRepositoryInfoById(repositoryDocument.id)
        }
        log.info("Scanning of {} repository completed successfully", repositoryDocument.sshUrl)
    } catch (e: Exception) {
        log.error("Scanning of ${repositoryDocument.sshUrl} repository ended in failure", e)
        openSearchService.saveRepositoriesInfo(sequenceOf(RepositoryInfoDocument(repositoryDocument)))
    }

    companion object {
        private val log = LoggerFactory.getLogger(IndexerServiceImpl::class.java)
    }
}