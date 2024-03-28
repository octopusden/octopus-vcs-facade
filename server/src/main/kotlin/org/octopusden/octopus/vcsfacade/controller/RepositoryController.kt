package org.octopusden.octopus.vcsfacade.controller

import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeResponse
import org.octopusden.octopus.vcsfacade.config.JobConfig
import org.octopusden.octopus.vcsfacade.dto.RepositoryResponse
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.VCSManager
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


@RestController
@RequestMapping("rest/api/1/repository")
class RepositoryController(
    private val jobProperties: JobConfig.JobProperties,
    private val vcsManager: VCSManager,
    @Qualifier("jobExecutor") private val jobExecutor: AsyncTaskExecutor
) {
    private val requestJobs = ConcurrentHashMap<String, Future<out VcsFacadeResponse>>()

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(vcsManager.getCommits(sshUrl, from, fromDate, to))
    }.data

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(@RequestParam("sshUrl") sshUrl: String, @RequestParam("commitId") commitId: String) =
        vcsManager.getCommit(sshUrl, commitId)

    @GetMapping("issues", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getIssuesFromCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(
            vcsManager.getCommits(sshUrl, from, fromDate, to)
                .flatMap { IssueKeyParser.findIssueKeys(it.message) }
                .distinct()
        )
    }.data


    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTags(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(vcsManager.getTags(sshUrl))
    }.data

    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        vcsManager.searchIssuesInRanges(searchRequest)
    }

    @PostMapping("pull-requests")
    fun createPullRequest(@RequestParam("sshUrl") sshUrl: String, @RequestBody createPullRequest: CreatePullRequest) =
        vcsManager.createPullRequest(sshUrl, createPullRequest)

    @GetMapping("find/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        vcsManager.find(issueKey)
    }

    @GetMapping("find/{issueKey}/branches", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findBranchesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(vcsManager.findBranches(issueKey))
    }.data

    @GetMapping("find/{issueKey}/commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(vcsManager.findCommits(issueKey))
    }.data

    @GetMapping("find/{issueKey}/pull-requests", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPullRequestsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        RepositoryResponse(vcsManager.findPullRequests(issueKey))
    }.data

    private fun <T : VcsFacadeResponse> processJob(requestId: String, func: () -> T): T {
        log.debug("Process request: {}", requestId)
        val future = requestJobs.computeIfAbsent(requestId) { processingRequest ->
            log.debug("Add job request: {}", processingRequest)
            val submittedFuture = jobExecutor.submit(Callable {
                log.debug("Start job request: {}", processingRequest)
                val result = func.invoke()
                log.trace("Finish job request: {}", processingRequest)
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
        log.debug("Return job result: {}", requestId)
        try {
            @Suppress("UNCHECKED_CAST")
            return requestJobs.remove(requestId)!!.get() as T
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RepositoryController::class.java)
    }
}
