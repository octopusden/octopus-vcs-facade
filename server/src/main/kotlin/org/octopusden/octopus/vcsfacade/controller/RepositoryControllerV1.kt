package org.octopusden.octopus.vcsfacade.controller

import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
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
@Deprecated(message = "Deprecated controller", replaceWith = ReplaceWith("RepositoryController"))
class RepositoryControllerV1(
    private val jobProperties: JobConfig.JobProperties,
    private val vcsManager: VcsManager,
    @Qualifier("jobExecutor") private val jobExecutor: AsyncTaskExecutor
) {
    private val requestJobs = ConcurrentHashMap<String, Future<*>>()

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Get commits ({},{}] in {} repository", (from ?: fromDate?.toString()).orEmpty(), to, sshUrl)
        RepositoryResponse(vcsManager.getCommits(sshUrl, from, fromDate, to))
    }.data.sorted().map { it.toV1() }

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(@RequestParam("sshUrl") sshUrl: String, @RequestParam("commitId") commitId: String): CommitV1 {
        log.warn("Deprecated call! Get commit {} in {} repository", commitId, sshUrl)
        return vcsManager.getCommit(sshUrl, commitId).toV1()
    }

    @GetMapping("issues", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getIssuesFromCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Find issue keys in commits ({},{}] in {} repository",
            (from ?: fromDate?.toString()).orEmpty(), to, sshUrl
        )
        RepositoryResponse(
            vcsManager.getCommits(sshUrl, from, fromDate, to)
                .flatMap { IssueKeyParser.findIssueKeys(it.message) }.distinct()
        )
    }.data.sorted()


    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTags(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Get tags in {} repository", sshUrl)
        RepositoryResponse(vcsManager.getTags(sshUrl))
    }.data.sorted().map { it.toV1() }

    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequestV1,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Search issue keys {} in specified commit ranges", searchRequest.issues)
        vcsManager.searchIssuesInRanges(searchRequest.toNew()).toV1()
    }

    @PostMapping("pull-requests")
    fun createPullRequest(
        @RequestParam("sshUrl") sshUrl: String, @RequestBody createPullRequest: CreatePullRequest
    ): PullRequest {
        log.info(
            "Create pull request ({} -> {}) in {} repository",
            sshUrl,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch
        )
        return vcsManager.createPullRequest(sshUrl, createPullRequest)
    }

    @GetMapping("find/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Get search summary for issue key {}", issueKey)
        vcsManager.find(issueKey)
    }

    @GetMapping("find/{issueKey}/branches", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findBranchesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find branches by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findBranches(issueKey))
    }.data.sorted().map { it.toV1() }

    @GetMapping("find/{issueKey}/commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find commits by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findCommits(issueKey))
    }.data.sorted().map { it.toV1() }

    @GetMapping("find/{issueKey}/pull-requests", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPullRequestsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find pull requests by issue key {}", issueKey)
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
        private val log = LoggerFactory.getLogger(RepositoryControllerV1::class.java)

        abstract class RefV1(
            val type: RefType,
            val name: String,
            val commitId: String,
            val link: String,
            val repository: Repository
        )

        class BranchV1(name: String, commitId: String, link: String, repository: Repository) :
            RefV1(RefType.BRANCH, name, commitId, link, repository)

        private fun Branch.toV1() = BranchV1(name, hash, link, repository)

        class TagV1(name: String, commitId: String, link: String, repository: Repository) :
            RefV1(RefType.TAG, name, commitId, link, repository)

        private fun Tag.toV1() = TagV1(name, hash, link, repository)

        data class CommitV1(
            val id: String,
            val message: String,
            val date: Date,
            val author: User,
            val parents: List<String>,
            val link: String,
            val repository: Repository
        )

        private fun Commit.toV1() = CommitV1(hash, message, date, author, parents, link, repository)

        data class RepositoryRangeV1(val sshUrl: String, val fromCid: String?, val fromDate: Date?, val toCid: String)

        private fun RepositoryRange.toV1() = RepositoryRangeV1(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

        private fun RepositoryRangeV1.toNew() = RepositoryRange(sshUrl, fromCid, fromDate, toCid)

        data class SearchIssuesInRangesRequestV1(val issues: Set<String>, val ranges: Set<RepositoryRangeV1>)

        private fun SearchIssuesInRangesRequestV1.toNew() =
            SearchIssuesInRangesRequest(issues, ranges.map { range -> range.toNew() }.toSet())

        data class SearchIssueInRangesResponseV1(val issueRanges: Map<String, Set<RepositoryRangeV1>>)

        private fun SearchIssueInRangesResponse.toV1() =
            SearchIssueInRangesResponseV1(issueRanges.mapValues { it.value.map { range -> range.toV1() }.toSet() })
    }
}
