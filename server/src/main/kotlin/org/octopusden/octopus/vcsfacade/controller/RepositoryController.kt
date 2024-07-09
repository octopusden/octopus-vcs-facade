package org.octopusden.octopus.vcsfacade.controller

import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.config.JobConfig
import org.octopusden.octopus.vcsfacade.dto.RepositoryResponse
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("rest/api/2/repository")
class RepositoryController(
    private val jobProperties: JobConfig.JobProperties,
    private val vcsManager: VcsManager,
    @Qualifier("jobExecutor") private val jobExecutor: AsyncTaskExecutor
) {
    private val requestJobs = ConcurrentHashMap<String, Future<*>>()

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("fromHashOrRef", required = false) fromHashOrRef: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestParam("toHashOrRef") toHashOrRef: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Get commits ({},{}] in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            sshUrl
        )
        RepositoryResponse(vcsManager.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef))
    }.data.sorted()

    @GetMapping("commits/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsWithFiles(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("fromHashOrRef", required = false) fromHashOrRef: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestParam("toHashOrRef") toHashOrRef: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Get commits ({},{}] with files (limit {}) in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            commitFilesLimit,
            sshUrl
        )
        RepositoryResponse(
            vcsManager.getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef)
                .map { it.mapFilesList(commitFilesLimit) }
        )
    }.data.sorted()

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("hashOrRef") hashOrRef: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get commit {} in {} repository", hashOrRef, sshUrl)
        vcsManager.getCommit(sshUrl, hashOrRef)
    }

    @GetMapping("commit/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitWithFiles(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("hashOrRef") hashOrRef: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get commit {} with files (limit {}) in {} repository", hashOrRef, commitFilesLimit, sshUrl)
        vcsManager.getCommitWithFiles(sshUrl, hashOrRef).mapFilesList(commitFilesLimit)
    }

    @GetMapping("issues", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getIssuesFromCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("fromHashOrRef", required = false) fromHashOrRef: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestParam("toHashOrRef") toHashOrRef: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Find issue keys in commits ({},{}] in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            sshUrl
        )
        RepositoryResponse(
            vcsManager.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)
                .flatMap { IssueKeyParser.findIssueKeys(it.message) }.distinct()
        )
    }.data.sorted()


    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTags(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get tags in {} repository", sshUrl)
        RepositoryResponse(vcsManager.getTags(sshUrl))
    }.data.sorted()

    @PostMapping("tags", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestBody createTag: CreateTag,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Create tag {} on {} in {} repository", createTag.name, createTag.hashOrRef, sshUrl)
        vcsManager.createTag(sshUrl, createTag)
    }

    @GetMapping("tag", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("name") name: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get tag {} in {} repository", name, sshUrl)
        vcsManager.getTag(sshUrl, name)
    }

    @DeleteMapping("tag", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deleteTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("name") name: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Delete tag {} in {} repository", name, sshUrl)
        vcsManager.deleteTag(sshUrl, name)
    }

    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Search issue keys {} in specified commit ranges", searchRequest.issueKeys)
        vcsManager.searchIssuesInRanges(searchRequest)
    }

    @PostMapping(
        "pull-requests",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun createPullRequest(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestBody createPullRequest: CreatePullRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Create pull request ({} -> {}) in {} repository",
            sshUrl,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch
        )
        vcsManager.createPullRequest(sshUrl, createPullRequest)
    }

    @GetMapping("find/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get search summary for issue key {}", issueKey)
        vcsManager.find(issueKey)
    }

    @GetMapping("find/{issueKey}/branches", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findBranchesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find branches by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findBranches(issueKey))
    }.data.sorted()

    @GetMapping("find/{issueKey}/commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find commits by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findCommits(issueKey))
    }.data.sorted()

    @GetMapping("find/{issueKey}/commits/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsWithFilesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find commits with files (limit {}) by issue key {}", commitFilesLimit, issueKey)
        RepositoryResponse(
            vcsManager.findCommitsWithFiles(issueKey).map { it.mapFilesList(commitFilesLimit) }
        )
    }.data.sorted()

    @GetMapping("find/{issueKey}/pull-requests", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPullRequestsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find pull requests by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findPullRequests(issueKey))
    }.data.sorted()

    private fun <T> processRequest(requestId: String, func: () -> T): T {
        with(requestJobs.computeIfAbsent(requestId) { newRequest ->
            log.debug("Submit request {}", newRequest)
            val future = jobExecutor.submit(Callable {
                log.trace("Start executing request {}", newRequest)
                val result = func.invoke()
                log.trace("Finish executing request {}", newRequest)
                result
            })
            val waitThreshold = Date(Date().time + (jobProperties.fastWorkTimoutSecs * 1000))
            while (Date().before(waitThreshold) && !future.isDone) {
                TimeUnit.MILLISECONDS.sleep(200)
            }
            future
        }) {
            if (!isDone) {
                throw JobProcessingException(
                    "Request $requestId is still processing",
                    requestId,
                    Date(Date().time + (jobProperties.retryIntervalSecs * 1000))
                )
            }
        }
        log.debug("Collect request {} result", requestId)
        try {
            @Suppress("UNCHECKED_CAST") return requestJobs.remove(requestId)!!.get() as T
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RepositoryController::class.java)

        private fun CommitWithFiles.mapFilesList(commitFilesLimit: Int) = files.sorted().let {
            CommitWithFiles(commit, totalFiles, if (commitFilesLimit > 0) it.take(commitFilesLimit) else it)
        }
    }
}
