package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeResponse
import org.octopusden.octopus.vcsfacade.config.JobProperties
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.VCSManager
import org.octopusden.octopus.vcsfacade.service.dto.RepositoryResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


@RestController
@RequestMapping("repository")
class RepositoryController(
    private val jobProperties: JobProperties,
    private val vcsManager: VCSManager,
    @Qualifier("jobExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    private val requestJobs = ConcurrentHashMap<String, Future<out VcsFacadeResponse>>()

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsForRelease(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ): Collection<Commit> = processJob(requestId ?: UUID.randomUUID().toString()) {
        val commits = vcsManager.getCommits(vcsPath, from, fromDate, to)
        RepositoryResponse(commits)
    }.data

    @Deprecated(
        message="Deprecated endpoint. Does not allow usage of a git ref containing slash as commitId",
        replaceWith = ReplaceWith("getCommit(vcsPath, commitId)")
    )
    @GetMapping("commits/{commitId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitById(@PathVariable("commitId") commitId: String, @RequestParam("vcsPath") vcsPath: String) =
        getCommit(vcsPath, commitId)

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(@RequestParam("vcsPath") vcsPath: String, @RequestParam("commitId") commitId: String) =
        vcsManager.findCommit(vcsPath, commitId)

    @GetMapping("issues", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsForReleaseIssues(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestParam("to") to: String,
        @RequestParam(name = "from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ): Collection<String> = processJob(requestId ?: UUID.randomUUID().toString()) {
        val issues =
            vcsManager.getCommits(vcsPath, from, fromDate, to)
                .flatMap { IssueKeyParser.findIssueKeys(it.message) }
                .distinct()
        RepositoryResponse(issues)
    }.data

    @GetMapping("issues/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ): Collection<Commit> =
        processJob(requestId ?: UUID.randomUUID().toString()) {
            val commits = vcsManager.findCommits(issueKey)
            RepositoryResponse(commits)
        }.data


    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTagsForRepository(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ): Collection<Tag> =
        processJob(requestId ?: UUID.randomUUID().toString()) {
            val commits = vcsManager.getTagsForRepository(vcsPath)
            RepositoryResponse(commits)
        }.data


    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ): SearchIssueInRangesResponse =
        processJob(requestId ?: UUID.randomUUID().toString()) {
            val issueRanges = vcsManager.getIssueRanges(searchRequest)
            SearchIssueInRangesResponse(issueRanges)
        }

    @PostMapping("pull-requests")
    fun createPullRequest(@RequestParam("vcsPath") vcsPath: String, @RequestBody pullRequestRequest: PullRequestRequest) =
        vcsManager.createPullRequest(vcsPath, pullRequestRequest)

    private fun <T : VcsFacadeResponse> processJob(requestId: String, func: () -> T): T {
        log.debug("Process request: $requestId")
        val future = requestJobs.computeIfAbsent(requestId) { processingRequest ->

            log.debug("Add job request: $processingRequest")
            val submittedFuture = taskExecutor.submit(Callable {
                log.debug("Start job request: $processingRequest")
                val result = func.invoke()
                log.trace("Finish job request: $processingRequest")
                result
            })

            val waitThreshold = Date(Date().time + (jobProperties.fastWorkTimoutSecs * 1000))
            while (Date().before(waitThreshold) && !submittedFuture.isDone) {
                TimeUnit.MILLISECONDS.sleep(200)
            }

            submittedFuture
        }

        if (!future.isDone) {
            throw JobProcessingException(
                "Job is processing",
                requestId,
                Date(Date().time + (jobProperties.retryIntervalSecs * 1000))
            )
        }

        log.debug("Return job result: $requestId")
        try {
            return requestJobs.remove(requestId)!!.get() as T
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RepositoryController::class.java)
    }
}
