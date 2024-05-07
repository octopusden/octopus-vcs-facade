package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.IndexReport
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType.GITEA
import org.octopusden.octopus.vcsfacade.service.GiteaIndexerService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDocument
import org.octopusden.octopus.vcsfacade.service.OpenSearchService.Companion.toDto
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toBranch
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService.Companion.toOrganizationAndRepository
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
    prefix = "vcs-facade",
    name = ["vcs.gitea.enabled", "opensearch.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GiteaIndexerServiceImpl(
    private val giteaService: GiteaService,
    private val openSearchService: OpenSearchService,
    @Qualifier("giteaIndexScanExecutor") private val giteaIndexScanExecutor: AsyncTaskExecutor,
    @Qualifier("isMaster") private val isMaster: Boolean
) : GiteaIndexerService {
    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.trace("=> registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
        val repositoryDocument = giteaCreateRefEvent.repository.toRepositoryDocument()
        checkInRepository(repositoryDocument)
        val refDocument = when (giteaCreateRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(
                giteaCreateRefEvent.ref, GiteaBranch.PayloadCommit(giteaCreateRefEvent.sha)
            ).toBranch(repositoryDocument.toDto()).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(giteaCreateRefEvent.ref, GiteaShortCommit(giteaCreateRefEvent.sha)).toTag(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)
        }
        openSearchService.saveRefs(listOf(refDocument))
        log.trace("<= registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
        val repositoryDocument = giteaDeleteRefEvent.repository.toRepositoryDocument()
        checkInRepository(repositoryDocument, true)
        val refDocumentId = when (giteaDeleteRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(giteaDeleteRefEvent.ref, GiteaBranch.PayloadCommit("unknown")).toBranch(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(
                giteaDeleteRefEvent.ref, GiteaShortCommit("unknown")
            ).toTag(repositoryDocument.toDto()).toDocument(repositoryDocument)
        }.id
        openSearchService.deleteRefsByIds(listOf(refDocumentId))
        log.trace("<= registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({})", giteaPushEvent)
        val repositoryDocument = giteaPushEvent.repository.toRepositoryDocument()
        checkInRepository(repositoryDocument)
        openSearchService.saveCommits(giteaPushEvent.commits.map {
            //IMPORTANT: commits in push event does not contain all demanded data, so it is required to get it from gitea directly
            giteaService.getCommit(repositoryDocument.group, repositoryDocument.name, it.id)
                .toDocument(repositoryDocument)
        })
        log.trace("<= registerGiteaPushEvent({})", giteaPushEvent)
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
        val repositoryDocument = giteaPullRequestEvent.repository.toRepositoryDocument()
        checkInRepository(repositoryDocument)
        val pullRequestDocument = giteaPullRequestEvent.pullRequest.toPullRequest(
            repositoryDocument.toDto(),
            //IMPORTANT: to calculate reviewers approves it is required to get pull request reviews from gitea directly
            giteaService.getPullRequestReviews(
                repositoryDocument.group, repositoryDocument.name, giteaPullRequestEvent.pullRequest.number
            )
        ).toDocument(repositoryDocument)
        openSearchService.savePullRequests(listOf(pullRequestDocument))
        log.trace("<= registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
    }

    override fun scheduleRepositoryScan(sshUrl: String) {
        log.trace("=> scheduleRepositoryScan({})", sshUrl)
        checkInRepository(sshUrl.toRepositoryDocument(), true)
        log.trace("<= scheduleRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(): IndexReport {
        log.trace("=> getIndexReport()")
        return IndexReport(openSearchService.findRepositoriesInfoByRepositoryType(GITEA).map {
            IndexReport.IndexReportRepository(
                giteaService.getSshUrl(it.repository.group, it.repository.name), it.lastScanAt
            )
        }).also {
            log.trace("=> getIndexReport(): {}", it)
        }
    }

    private fun GiteaRepository.toRepositoryDocument(): RepositoryDocument {
        val (organization, repository) = toOrganizationAndRepository()
        return with(giteaService.toRepository(this)) {
            RepositoryDocument(GITEA, organization, repository, sshUrl, link, avatar)
        }
    }

    private fun String.toRepositoryDocument(): RepositoryDocument {
        if (!giteaService.isSupport(this)) {
            throw IllegalStateException("Repository `$this` is not supported")
        }
        val (group, name) = giteaService.parse(this)
        return giteaService.getRepository(group, name).let {
            RepositoryDocument(GITEA, group, name, it.sshUrl, it.link, it.avatar)
        }
    }

    private fun checkInRepository(
        repositoryDocument: RepositoryDocument, rescan: Boolean = false
    ) {
        openSearchService.findRepositoryInfoByRepositoryId(repositoryDocument.id)?.let {
            if (rescan) {
                it.scanRequired = true
            }
            openSearchService.saveRepositoriesInfo(listOf(it))
        } ?: openSearchService.saveRepositoriesInfo(listOf(RepositoryInfoDocument(repositoryDocument)))
    }

    @Scheduled(cron = "#{ @giteaIndexScheduleRepositoriesRescanCron }")
    private fun scheduleRepositoriesRescan() = if (isMaster) {
        try {
            val repositoriesInfo = openSearchService.findRepositoriesInfoByRepositoryType(GITEA).map {
                it.apply { scanRequired = true }
            }.toSet() + giteaService.getRepositories().map { RepositoryInfoDocument(it.sshUrl.toRepositoryDocument()) }
            logIndexActionMessage("Scheduled ${repositoriesInfo.size} $GITEA repositories for scan", repositoriesInfo)
            openSearchService.saveRepositoriesInfo(repositoriesInfo.toList())
        } catch (e: Exception) {
            log.error("Unable to schedule $GITEA repositories for rescan", e)
        }
    } else {
        log.debug("Suppress {} gitea repositories rescan scheduling on non-master instance", GITEA)
    }

    private val repositoryScans = ConcurrentHashMap<RepositoryInfoDocument, Future<*>>()

    @Scheduled(fixedDelayString = "#{ @giteaIndexSubmitScheduledRepositoriesScanFixedDelay }")
    private fun submitScheduledRepositoriesScan() = if (isMaster) {
        try {
            repositoryScans.filterValues { it.isDone }.keys.forEach {
                repositoryScans.remove(it)
            }
            openSearchService.findRepositoriesInfoByRepositoryType(GITEA).filter { it.scanRequired }.forEach {
                repositoryScans.computeIfAbsent(it) { repositoryInfoDocument ->
                    giteaIndexScanExecutor.submit { repositoryInfoDocument.scan() }
                }
            }
            logIndexActionMessage(
                "${repositoryScans.size} $GITEA repositories in queue for scan", repositoryScans.keys
            )
        } catch (e: Exception) {
            log.error("Unable to submit $GITEA repositories for scan", e)
        }
    } else {
        log.debug("Suppress {} gitea repositories scan submitting on non-master instance", GITEA)
    }

    private fun RepositoryInfoDocument.scan() = try {
        if (giteaService.isRepositoryExist(repository.group, repository.name)) {
            scanRequired = false
            lastScanAt = Date()
            openSearchService.saveRepositoriesInfo(listOf(this))
            val indexRefsIds = openSearchService.findRefsByRepositoryId(repository.id).map { it.id }
            val refs = giteaService.getBranches(repository.group, repository.name).map { it.toDocument(repository) } +
                    giteaService.getTags(repository.group, repository.name).map { it.toDocument(repository) }
            val orphanedRefsIds = indexRefsIds - refs.map { it.id }.toSet()
            logIndexActionMessage(
                "Remove ${orphanedRefsIds.size} ref(s) from index for `${repository.fullName}` $GITEA repository",
                orphanedRefsIds
            )
            openSearchService.deleteRefsByIds(orphanedRefsIds)
            logIndexActionMessage(
                "Save ${refs.size} ref(s) in index for `${repository.fullName}` $GITEA repository ", refs
            )
            openSearchService.saveRefs(refs)
            val indexCommitsIds = openSearchService.findCommitsByRepositoryId(repository.id).map { it.id }
            val commits = giteaService.getBranchesCommitGraph(repository.group, repository.name)
                .map { it.toDocument(repository) }
            val orphanedCommitsIds = indexCommitsIds - commits.map { it.id }.toSet()
            logIndexActionMessage(
                "Remove ${orphanedCommitsIds.size} commit(s) from index for `${repository.fullName}` $GITEA repository",
                orphanedCommitsIds
            )
            openSearchService.deleteCommitsByIds(orphanedCommitsIds)
            logIndexActionMessage(
                "Save ${commits.size} commits(s) in index for `${repository.fullName}` $GITEA repository ", commits
            )
            openSearchService.saveCommits(commits)
            val indexPullRequestsIds = openSearchService.findPullRequestsByRepositoryId(repository.id).map { it.id }
            val pullRequests = try {
                giteaService.getPullRequests(repository.group, repository.name)
            } catch (e: NotFoundException) {
                emptyList() //for some reason Gitea returns 404 in case of empty repository
            }.map { it.toDocument(repository) }
            val orphanedPullRequestsIds = indexPullRequestsIds - pullRequests.map { it.id }.toSet()
            logIndexActionMessage(
                "Remove ${orphanedPullRequestsIds.size} pull request(s) from index for `${repository.fullName}` $GITEA repository",
                orphanedPullRequestsIds
            )
            openSearchService.deletePullRequestsByIds(orphanedPullRequestsIds)
            logIndexActionMessage(
                "Save ${pullRequests.size} pull request(s) in index for `${repository.fullName}` $GITEA repository ",
                pullRequests
            )
            openSearchService.savePullRequests(pullRequests)
        } else {
            log.debug("Remove `{}` {} repository pull-requests from index", repository.fullName, GITEA)
            openSearchService.deletePullRequestsByRepositoryId(repository.id)
            log.debug("Remove `{}` {} repository commits from index", repository.fullName, GITEA)
            openSearchService.deleteCommitsByRepositoryId(repository.id)
            log.debug("Remove `{}` {} repository refs from index", repository.fullName, GITEA)
            openSearchService.deleteRefsByRepositoryId(repository.id)
            log.debug("Remove `{}` {} repository from index", repository.fullName, GITEA)
            openSearchService.deleteRepositoryInfo(this)
        }
        log.info("Scanning of `{}` {} repository completed successfully", repository.fullName, GITEA)
    } catch (e: Exception) {
        log.error("Scanning of `${repository.fullName}` $GITEA repository ended in failure", e)
        scanRequired = true
        openSearchService.saveRepositoriesInfo(listOf(this))
    }

    companion object {
        private val log = LoggerFactory.getLogger(GiteaIndexerServiceImpl::class.java)

        private fun logIndexActionMessage(message: String, documents: Collection<Any>) {
            if (log.isTraceEnabled) {
                log.trace("$message: $documents")
            } else if (log.isDebugEnabled) {
                log.debug(message)
            }
        }
    }
}