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
    prefix = "vcs-facade", name = ["vcs.gitea.enabled", "opensearch.enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaIndexerServiceImpl(
    private val giteaService: GiteaService,
    private val openSearchService: OpenSearchService,
    @Qualifier("giteaIndexScanExecutor") private val giteaIndexScanExecutor: AsyncTaskExecutor
) : GiteaIndexerService {

    @Scheduled(cron = "#{ @giteaIndexScanCron }")
    private fun scan() = submitRepositoriesScan(false)

    @Scheduled(cron = "#{ @giteaIndexScanReindexCron }")
    private fun reindexScan() = submitRepositoriesScan(true)

    override fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent) {
        log.trace("=> registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
        openSearchService.saveRefs(listOf(giteaCreateRefEvent.toDocument()))
        log.trace("<= registerGiteaCreateRefEvent({})", giteaCreateRefEvent)
    }

    override fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent) {
        log.trace("=> registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
        openSearchService.deleteRefsByIds(listOf(giteaDeleteRefEvent.toDocumentId()))
        log.trace("<= registerGiteaDeleteRefEvent({})", giteaDeleteRefEvent)
    }

    override fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent) {
        log.trace("=> registerGiteaPushEvent({})", giteaPushEvent)
        openSearchService.saveCommits(giteaPushEvent.toDocuments())
        log.trace("<= registerGiteaPushEvent({})", giteaPushEvent)
    }

    override fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent) {
        log.trace("=> registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
        openSearchService.savePullRequests(listOf(giteaPullRequestEvent.toDocument()))
        log.trace("<= registerGiteaPullRequestEvent({})", giteaPullRequestEvent)
    }

    override fun submitRepositoryScan(sshUrl: String, reindex: Boolean) {
        log.trace("=> submitRepositoryScan({})", sshUrl)
        submitRepositoryScan(sshUrl.toRepository(), reindex)
        log.trace("<= submitRepositoryScan({})", sshUrl)
    }

    override fun getIndexReport(): IndexReport {
        log.trace("=> getIndexReport()")
        return IndexReport(openSearchService.getRepositories(GITEA).map { it.toIndexReportRepository() }).also {
            log.trace("=> getIndexReport(): {}", it)
        }
    }

    private fun submitRepositoriesScan(reindex: Boolean) = try {
        log.trace("Submitting {} repositories for {}scan", GITEA, if (reindex) "reindexing " else "")
        val repositories = openSearchService.getRepositories(GITEA) + giteaService.getRepositories()
            .map { it.sshUrl.toRepository() }
        log.trace("Repositories collected for {}scan: {}", if (reindex) "reindexing " else "", repositories)
        repositories.forEach { submitRepositoryScan(it, reindex) }
        log.debug(
            "Submitted {} {} repositories for {}scan", repositories.size, GITEA, if (reindex) "reindexing " else ""
        )
    } catch (e: Exception) {
        log.error("Unable to submit $GITEA repositories for ${if (reindex) "reindexing " else ""}scan", e)
    }

    private fun submitRepositoryScan(repository: Repository, reindex: Boolean) = giteaIndexScanExecutor.submit {
        try {
            val openSearchService = openSearchService
            if (giteaService.isRepositoryExist(repository.group, repository.name)) {
                with(
                    openSearchService.findRepositoryById(repository.id) ?: openSearchService.saveRepository(repository)
                ) {
                    val refs = giteaService.getBranches(group, name).map { it.toDocument(id) } +
                            giteaService.getTags(group, name).map { it.toDocument(id) }
                    val indexRefs = openSearchService.findRefsByRepositoryId(id)
                    val orphanedRefsIds = refs.toSet().let { refsSet ->
                        indexRefs.mapNotNull { if (refsSet.contains(it)) null else it.id }
                    }
                    logRepositoryScanMessage(
                        "Remove ${orphanedRefsIds.size} ref(s) from index for `$fullName` $GITEA repository",
                        orphanedRefsIds
                    )
                    openSearchService.deleteRefsByIds(orphanedRefsIds)
                    if (reindex) {
                        logRepositoryScanMessage(
                            "Update ${refs.size} ref(s) in index for `$fullName` $GITEA repository ",
                            refs
                        )
                        openSearchService.saveRefs(refs)
                    } else {
                        val missingRefs = refs.filter { !indexRefs.contains(it) }
                        logRepositoryScanMessage(
                            "Register ${missingRefs.size} ref(s) in index for `$fullName` $GITEA repository ",
                            missingRefs
                        )
                        openSearchService.saveRefs(missingRefs)
                    }
                    val commits = giteaService.getBranchesCommitGraph(group, name).map { it.toDocument(id) }
                    val indexCommits = openSearchService.findCommitsByRepositoryId(id)
                    val orphanedCommitsIds = commits.toSet().let { commitsSet ->
                        indexCommits.mapNotNull { if (commitsSet.contains(it)) null else it.id }
                    }
                    logRepositoryScanMessage(
                        "Remove ${orphanedCommitsIds.size} commit(s) from index for `$fullName` $GITEA repository",
                        orphanedCommitsIds
                    )
                    openSearchService.deleteCommitsByIds(orphanedCommitsIds)
                    if (reindex) {
                        logRepositoryScanMessage(
                            "Update ${commits.size} commits(s) in index for `$fullName` $GITEA repository ",
                            commits
                        )
                        openSearchService.saveCommits(commits)
                    } else {
                        val missingCommits = commits.filter { !indexCommits.contains(it) }
                        logRepositoryScanMessage(
                            "Register ${missingCommits.size} commit(s) in index for `$fullName` $GITEA repository ",
                            missingCommits
                        )
                        openSearchService.saveCommits(missingCommits)
                    }
                    val pullRequests = try {
                        giteaService.getPullRequests(group, name)
                    } catch (e: NotFoundException) {
                        emptyList() //for some reason Gitea returns 404 in case of empty repository
                    }.map { it.toDocument(id) }
                    val indexPullRequests = openSearchService.findPullRequestsByRepositoryId(id)
                    val orphanedPullRequestsIds = pullRequests.toSet().let { pullRequestsSet ->
                        indexPullRequests.mapNotNull { if (pullRequestsSet.contains(it)) null else it.id }
                    }
                    logRepositoryScanMessage(
                        "Remove ${orphanedPullRequestsIds.size} pull request(s) from index for `$fullName` $GITEA repository",
                        orphanedPullRequestsIds
                    )
                    openSearchService.deletePullRequestsByIds(orphanedPullRequestsIds)
                    if (reindex) {
                        logRepositoryScanMessage(
                            "Update ${pullRequests.size} pull request(s) in index for `$fullName` $GITEA repository ",
                            pullRequests
                        )
                        openSearchService.savePullRequests(pullRequests)
                    } else {
                        val missingPullRequests = pullRequests.filter { !indexPullRequests.contains(it) }
                        logRepositoryScanMessage(
                            "Register ${missingPullRequests.size} pull request(s) in index for `$fullName` $GITEA repository ",
                            missingPullRequests
                        )
                        openSearchService.savePullRequests(missingPullRequests)
                    }
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
                log.debug("Remove `{}` {} repository from index", repository.fullName, GITEA)
                openSearchService.deleteRepository(repository)
            }
            log.info("Scanning of `{}` {} repository completed successfully", repository.fullName, GITEA)
        } catch (e: Exception) {
            log.error("Scanning of `${repository.fullName}` $GITEA repository ended in failure", e)
        }
    }

    private fun registerGiteaRepository(giteaRepository: GiteaRepository): Repository {
        val openSearchService = openSearchService
        val repository = giteaRepository.toDocument()
        return openSearchService.findRepositoryById(repository.id) ?: openSearchService.saveRepository(repository)
    }

    private fun String.toRepository(): Repository {
        if (!giteaService.isSupport(this)) {
            throw IllegalStateException("Repository `$this` is not supported")
        }
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

        private fun logRepositoryScanMessage(message: String, documents: Collection<Any>) {
            if (log.isTraceEnabled) {
                log.trace("$message: $documents")
            } else if (log.isDebugEnabled) {
                log.debug(message)
            }
        }
    }
}