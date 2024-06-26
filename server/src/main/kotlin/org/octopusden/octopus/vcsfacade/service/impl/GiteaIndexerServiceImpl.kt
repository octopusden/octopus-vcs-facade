package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import java.util.concurrent.Future
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
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
        val repositoryDocument = giteaService.toRepository(giteaCreateRefEvent.repository).toRepositoryDocument()
        checkInRepository(repositoryDocument)
        val refDocument = when (giteaCreateRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(
                giteaCreateRefEvent.ref, GiteaBranch.PayloadCommit(giteaCreateRefEvent.sha)
            ).toBranch(repositoryDocument.toDto()).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(giteaCreateRefEvent.ref, GiteaShortCommit(giteaCreateRefEvent.sha)).toTag(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)
        }
        openSearchService.saveRefs(sequenceOf(refDocument))
        log.trace("<= registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
        val repositoryDocument = giteaService.toRepository(giteaDeleteRefEvent.repository).toRepositoryDocument()
        checkInRepository(repositoryDocument, true)
        val refDocumentId = when (giteaDeleteRefEvent.refType.refType) {
            RefType.BRANCH -> GiteaBranch(giteaDeleteRefEvent.ref, GiteaBranch.PayloadCommit("unknown")).toBranch(
                repositoryDocument.toDto()
            ).toDocument(repositoryDocument)

            RefType.TAG -> GiteaTag(
                giteaDeleteRefEvent.ref, GiteaShortCommit("unknown")
            ).toTag(repositoryDocument.toDto()).toDocument(repositoryDocument)
        }.id
        openSearchService.deleteRefsByIds(sequenceOf(refDocumentId))
        log.trace("<= registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({})", giteaPushEvent)
        val repositoryDocument = giteaService.toRepository(giteaPushEvent.repository).toRepositoryDocument()
        checkInRepository(repositoryDocument)
        openSearchService.saveCommits(giteaPushEvent.commits.asSequence().map {
            //IMPORTANT: commits in push event does not contain all demanded data, so it is required to get it from gitea directly
            giteaService.getCommitWithFiles(repositoryDocument.group, repositoryDocument.name, it.id)
                .toDocument(repositoryDocument)
        })
        log.trace("<= registerGiteaPushEvent({})", giteaPushEvent)
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
        val repositoryDocument = giteaService.toRepository(giteaPullRequestEvent.repository).toRepositoryDocument()
        checkInRepository(repositoryDocument)
        val pullRequestDocument = giteaPullRequestEvent.pullRequest.toPullRequest(
            repositoryDocument.toDto(),
            //IMPORTANT: to calculate reviewers approves it is required to get pull request reviews from gitea directly
            giteaService.getPullRequestReviews(
                repositoryDocument.group, repositoryDocument.name, giteaPullRequestEvent.pullRequest.number
            )
        ).toDocument(repositoryDocument)
        openSearchService.savePullRequests(sequenceOf(pullRequestDocument))
        log.trace("<= registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
    }

    override fun scheduleRepositoryScan(sshUrl: String) {
        log.trace("=> scheduleRepositoryScan({})", sshUrl)
        checkInRepository(Repository(sshUrl.lowercase(), "unknown").toRepositoryDocument(), true)
        log.trace("<= scheduleRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(): IndexReport {
        log.trace("=> getIndexReport()")
        return IndexReport(openSearchService.findRepositoriesInfoByRepositoryType(GITEA).map {
            IndexReport.IndexReportRepository(it.repository.sshUrl, it.scanRequired, it.lastScanAt)
        }).also { log.trace("=> getIndexReport(): {}", it) }
    }

    private fun Repository.toRepositoryDocument(): RepositoryDocument {
        if (!giteaService.isSupport(sshUrl)) {
            throw IllegalStateException("$sshUrl is not supported $GITEA repository")
        }
        val (group, name) = giteaService.parse(sshUrl)
        return RepositoryDocument(GITEA, group, name, sshUrl, link, avatar)
    }

    private fun checkInRepository(repositoryDocument: RepositoryDocument, rescan: Boolean = false) {
        openSearchService.findRepositoryInfoById(repositoryDocument.id)?.let {
            if (rescan) {
                openSearchService.saveRepositoriesInfo(sequenceOf(it.apply { scanRequired = true }))
            }
        } ?: openSearchService.saveRepositoriesInfo(sequenceOf(RepositoryInfoDocument(repositoryDocument)))
    }

    @Scheduled(cron = "#{ @giteaIndexScheduleRepositoriesRescanCron }")
    private fun scheduleRepositoriesRescan() = if (isMaster) {
        try {
            val repositoriesInfo = openSearchService.findRepositoriesInfoByRepositoryType(GITEA).map {
                it.apply { scanRequired = true }
            }.toSet() + giteaService.getRepositories().map {
                RepositoryInfoDocument(it.toRepositoryDocument())
            }
            logIndexActionMessage(
                "Scheduled ${repositoriesInfo.size} $GITEA repositories for scan",
                repositoriesInfo.asSequence()
            )
            openSearchService.saveRepositoriesInfo(repositoriesInfo.asSequence())
        } catch (e: Exception) {
            log.error("Unable to schedule $GITEA repositories for rescan", e)
        }
    } else {
        log.trace("Suppress {} gitea repositories rescan scheduling on non-master instance", GITEA)
    }

    private val repositoryScanQueue = mutableMapOf<RepositoryInfoDocument, Future<*>>()

    @Scheduled(fixedDelayString = "#{ @giteaIndexSubmitScheduledRepositoriesScanFixedDelay }")
    private fun submitScheduledRepositoriesScan() = if (isMaster) {
        try {
            repositoryScanQueue.filterValues { it.isDone }.keys.forEach {
                repositoryScanQueue.remove(it)
            }
            openSearchService.findRepositoriesInfoByRepositoryType(GITEA).filter { it.scanRequired }.forEach {
                repositoryScanQueue.computeIfAbsent(it) { repositoryInfoDocument ->
                    giteaIndexScanExecutor.submit { scan(repositoryInfoDocument.repository) }
                }
            }
            logIndexActionMessage(
                "${repositoryScanQueue.size} $GITEA repositories in scan queue", repositoryScanQueue.keys.asSequence()
            )
        } catch (e: Exception) {
            log.error("Unable to submit $GITEA repositories for scan", e)
        }
    } else {
        log.trace("Suppress {} repositories scan submitting on non-master instance", GITEA)
    }

    private fun scan(repositoryDocument: RepositoryDocument) = try {
        giteaService.findRepository(repositoryDocument.group, repositoryDocument.name)?.let { foundRepository ->
            with(RepositoryInfoDocument(foundRepository.toRepositoryDocument(), false, Date())) {
                log.debug("Save repository info in index for {} {} repository", repository.fullName(), GITEA)
                openSearchService.saveRepositoriesInfo(sequenceOf(this))
                val branches = giteaService.getBranches(repository.group, repository.name).map {
                    it.toDocument(repository)
                }
                val tags = giteaService.getTags(repository.group, repository.name).map {
                    it.toDocument(repository)
                }
                val orphanedRefsIds = (openSearchService.findRefsIdsByRepositoryId(repository.id) -
                        (branches.map { it.id } + tags.map { it.id }).toSet()).asSequence()
                logIndexActionMessage(
                    "Remove orphaned refs from index for ${repository.fullName()} $GITEA repository",
                    orphanedRefsIds
                )
                openSearchService.deleteRefsByIds(orphanedRefsIds)
                logIndexActionMessage(
                    "Save branches in index for ${repository.fullName()} $GITEA repository", branches
                )
                openSearchService.saveRefs(branches)
                logIndexActionMessage(
                    "Save tags in index for ${repository.fullName()} $GITEA repository", tags
                )
                openSearchService.saveRefs(tags)
                val commits = giteaService.getBranchesCommitGraph(repository.group, repository.name).map {
                    it.toDocument(repository)
                }
                val orphanedCommitsIds = (openSearchService.findCommitsIdsByRepositoryId(repository.id) -
                        commits.map { it.id }.toSet()).asSequence()
                logIndexActionMessage(
                    "Remove orphaned commits from index for ${repository.fullName()} $GITEA repository",
                    orphanedCommitsIds
                )
                openSearchService.deleteCommitsByIds(orphanedCommitsIds)
                logIndexActionMessage(
                    "Save commits in index for ${repository.fullName()} $GITEA repository ", commits
                )
                openSearchService.saveCommits(commits)
                val pullRequests = try {
                    giteaService.getPullRequests(repository.group, repository.name)
                } catch (e: NotFoundException) {
                    emptySequence() //for some reason Gitea returns 404 in case of empty repository
                }.map { it.toDocument(repository) }
                val orphanedPullRequestsIds = (openSearchService.findPullRequestsIdsByRepositoryId(repository.id) -
                        pullRequests.map { it.id }.toSet()).asSequence()
                logIndexActionMessage(
                    "Remove orphaned pull requests from index for ${repository.fullName()} $GITEA repository",
                    orphanedPullRequestsIds
                )
                openSearchService.deletePullRequestsByIds(orphanedPullRequestsIds)
                logIndexActionMessage(
                    "Save pull requests in index for ${repository.fullName()} $GITEA repository ", pullRequests
                )
                openSearchService.savePullRequests(pullRequests)
            }
        } ?: run {
            log.debug("Remove {} {} repository pull-requests from index", repositoryDocument.fullName(), GITEA)
            openSearchService.deletePullRequestsByRepositoryId(repositoryDocument.id)
            log.debug("Remove {} {} repository commits from index", repositoryDocument.fullName(), GITEA)
            openSearchService.deleteCommitsByRepositoryId(repositoryDocument.id)
            log.debug("Remove {} {} repository refs from index", repositoryDocument.fullName(), GITEA)
            openSearchService.deleteRefsByRepositoryId(repositoryDocument.id)
            log.debug("Remove repository info from index for {} {} repository", repositoryDocument.fullName(), GITEA)
            openSearchService.deleteRepositoryInfoById(repositoryDocument.id)
        }
        log.info("Scanning of {} {} repository completed successfully", repositoryDocument.fullName(), GITEA)
    } catch (e: Exception) {
        log.error("Scanning of ${repositoryDocument.fullName()} $GITEA repository ended in failure", e)
        openSearchService.saveRepositoriesInfo(sequenceOf(RepositoryInfoDocument(repositoryDocument)))
    }

    companion object {
        private val log = LoggerFactory.getLogger(GiteaIndexerServiceImpl::class.java)

        private fun RepositoryDocument.fullName() = "$group/$name"

        private fun logIndexActionMessage(message: String, documents: Sequence<Any>) {
            if (log.isTraceEnabled) {
                log.trace("$message: $documents")
            } else if (log.isDebugEnabled) {
                log.debug(message)
            }
        }
    }
}