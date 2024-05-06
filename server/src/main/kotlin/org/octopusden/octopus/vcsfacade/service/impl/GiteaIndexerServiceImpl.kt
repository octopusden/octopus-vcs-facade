package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
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

    @Scheduled(cron = "#{ @giteaIndexScanCron }")
    private fun scan() = if (isMaster) {
        try {
            log.trace("Submitting {} repositories for scan", GITEA)
            val repositories = openSearchService.getRepositories(GITEA) + giteaService.getRepositories()
                .map { it.sshUrl.toRepositoryDocument() }
            log.trace("Repositories collected for scan: {}", repositories)
            repositories.forEach { submitRepositoryScan(it) }
            log.debug("Submitted {} {} repositories for scan", repositories.size, GITEA)
        } catch (e: Exception) {
            log.error("Unable to submit $GITEA repositories for scan", e)
        }
    } else {
        log.debug("Suppress {} gitea repositories scan on non-master instance", GITEA)
    }

    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.trace("=> registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
        openSearchService.saveRefs(listOf(giteaCreateRefEvent.toRefDocument()))
        log.trace("<= registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
        openSearchService.deleteRefsByIds(listOf(giteaDeleteRefEvent.toRefDocumentId()))
        log.trace("<= registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({})", giteaPushEvent)
        openSearchService.saveCommits(giteaPushEvent.toCommitDocuments())
        log.trace("<= registerGiteaPushEvent({})", giteaPushEvent)
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
        val indexPullRequest = giteaPullRequestEvent.toPullRequestDocument()
        val removeOrphanedCommits = if (indexPullRequest.status == PullRequestStatus.MERGED)
            openSearchService.findPullRequestById(indexPullRequest.id)?.status != PullRequestStatus.MERGED
        else false
        openSearchService.savePullRequests(listOf(indexPullRequest))
        if (removeOrphanedCommits) {
            val indexCommitsIds =
                openSearchService.findCommitsByRepositoryId(indexPullRequest.repository.id).map { it.id }
            val commitsIds = giteaService.getBranchesCommitGraph(
                indexPullRequest.repository.group,
                indexPullRequest.repository.name
            ).map { it.toDocument(indexPullRequest.repository).id }.toSet()
            val orphanedCommitsIds = indexCommitsIds - commitsIds
            logIndexActionMessage(
                "Remove ${orphanedCommitsIds.size} commit(s) from index for `${indexPullRequest.repository.fullName}` $GITEA repository",
                orphanedCommitsIds
            )
            openSearchService.deleteCommitsByIds(orphanedCommitsIds)
        }
        log.trace("<= registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
    }

    override fun submitRepositoryScan(sshUrl: String) {
        log.trace("=> submitRepositoryScan({})", sshUrl)
        submitRepositoryScan(sshUrl.toRepositoryDocument())
        log.trace("<= submitRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(): IndexReport {
        log.trace("=> getIndexReport()")
        return IndexReport(openSearchService.getRepositories(GITEA).map { it.toIndexReportRepository() }).also {
            log.trace("=> getIndexReport(): {}", it)
        }
    }

    private fun submitRepositoryScan(repositoryDocument: RepositoryDocument) =
        giteaIndexScanExecutor.submit {
            try {
                if (giteaService.isRepositoryExist(repositoryDocument.group, repositoryDocument.name)) {
                    with(openSearchService.saveRepository(repositoryDocument.apply { lastScanAt = Date() })) {
                        val indexRefsIds = openSearchService.findRefsByRepositoryId(id).map { it.id }
                        val refs = giteaService.getBranches(group, name).map { it.toDocument(this) } +
                                giteaService.getTags(group, name).map { it.toDocument(this) }
                        val orphanedRefsIds = indexRefsIds - refs.map { it.id }.toSet()
                        logIndexActionMessage(
                            "Remove ${orphanedRefsIds.size} ref(s) from index for `$fullName` $GITEA repository",
                            orphanedRefsIds
                        )
                        openSearchService.deleteRefsByIds(orphanedRefsIds)
                        logIndexActionMessage(
                            "Save ${refs.size} ref(s) in index for `$fullName` $GITEA repository ",
                            refs
                        )
                        openSearchService.saveRefs(refs)
                        val indexCommitsIds = openSearchService.findCommitsByRepositoryId(id).map { it.id }
                        val commits = giteaService.getBranchesCommitGraph(group, name).map { it.toDocument(this) }
                        val orphanedCommitsIds = indexCommitsIds - commits.map { it.id }.toSet()
                        logIndexActionMessage(
                            "Remove ${orphanedCommitsIds.size} commit(s) from index for `$fullName` $GITEA repository",
                            orphanedCommitsIds
                        )
                        openSearchService.deleteCommitsByIds(orphanedCommitsIds)
                        logIndexActionMessage(
                            "Save ${commits.size} commits(s) in index for `$fullName` $GITEA repository ",
                            commits
                        )
                        openSearchService.saveCommits(commits)
                        val indexPullRequestsIds = openSearchService.findPullRequestsByRepositoryId(id).map { it.id }
                        val pullRequests = try {
                            giteaService.getPullRequests(group, name)
                        } catch (e: NotFoundException) {
                            emptyList() //for some reason Gitea returns 404 in case of empty repository
                        }.map { it.toDocument(this) }
                        val orphanedPullRequestsIds = indexPullRequestsIds - pullRequests.map { it.id }.toSet()
                        logIndexActionMessage(
                            "Remove ${orphanedPullRequestsIds.size} pull request(s) from index for `$fullName` $GITEA repository",
                            orphanedPullRequestsIds
                        )
                        openSearchService.deletePullRequestsByIds(orphanedPullRequestsIds)
                        logIndexActionMessage(
                            "Save ${pullRequests.size} pull request(s) in index for `$fullName` $GITEA repository ",
                            pullRequests
                        )
                        openSearchService.savePullRequests(pullRequests)
                    }
                } else {
                    log.debug("Remove `{}` {} repository pull-requests from index", repositoryDocument.fullName, GITEA)
                    openSearchService.deletePullRequestsByRepositoryId(repositoryDocument.id)
                    log.debug("Remove `{}` {} repository commits from index", repositoryDocument.fullName, GITEA)
                    openSearchService.deleteCommitsByRepositoryId(repositoryDocument.id)
                    log.debug("Remove `{}` {} repository refs from index", repositoryDocument.fullName, GITEA)
                    openSearchService.deleteRefsByRepositoryId(repositoryDocument.id)
                    log.debug("Remove `{}` {} repository from index", repositoryDocument.fullName, GITEA)
                    openSearchService.deleteRepository(repositoryDocument)
                }
                log.info("Scanning of `{}` {} repository completed successfully", repositoryDocument.fullName, GITEA)
            } catch (e: Exception) {
                log.error("Scanning of `${repositoryDocument.fullName}` $GITEA repository ended in failure", e)
            }
        }

    private fun registerGiteaRepository(giteaRepository: GiteaRepository): RepositoryDocument {
        val (organization, repository) = giteaRepository.toOrganizationAndRepository()
        return with(giteaService.toRepository(giteaRepository)) {
            RepositoryDocument(GITEA, organization, repository, sshUrl, link, avatar).let {
                openSearchService.findRepositoryById(it.id)?.apply { lastScanAt = null }
                    ?: openSearchService.saveRepository(it)
            }
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

    private fun RepositoryDocument.toIndexReportRepository() = IndexReport.IndexReportRepository(
        giteaService.getSshUrl(group, name), lastScanAt
    )

    private fun GiteaCreateRefEvent.toRefDocument() = when (refType.refType) {
        RefType.BRANCH -> GiteaBranch(ref, GiteaBranch.PayloadCommit(sha))
            .toBranch(giteaService.toRepository(repository))
            .toDocument(registerGiteaRepository(repository))

        RefType.TAG -> GiteaTag(ref, GiteaShortCommit(sha))
            .toTag(giteaService.toRepository(repository))
            .toDocument(registerGiteaRepository(repository))
    }

    private fun GiteaDeleteRefEvent.toRefDocumentId() = when (refType.refType) {
        RefType.BRANCH -> GiteaBranch(ref, GiteaBranch.PayloadCommit("unknown"))
            .toBranch(giteaService.toRepository(repository))
            .toDocument(registerGiteaRepository(repository))

        RefType.TAG -> GiteaTag(ref, GiteaShortCommit("unknown"))
            .toTag(giteaService.toRepository(repository))
            .toDocument(registerGiteaRepository(repository))
    }.id

    private fun GiteaPushEvent.toCommitDocuments() = registerGiteaRepository(repository).let {
        //IMPORTANT: commits in push event does not contain all demanded data, so it is required to get it from gitea directly
        commits.map { commit -> giteaService.getCommit(it.group, it.name, commit.id).toDocument(it) }
    }

    private fun GiteaPullRequestEvent.toPullRequestDocument() = registerGiteaRepository(repository).let {
        //IMPORTANT: to calculate reviewers approves it is required to get pull request reviews from gitea directly
        pullRequest.toPullRequest(it.toDto(), giteaService.getPullRequestReviews(it.group, it.name, pullRequest.number))
            .toDocument(it)
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