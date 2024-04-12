package org.octopusden.octopus.vcsfacade.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date
import java.util.Objects
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
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
@RequestMapping("repository")
@Deprecated(message = "Deprecated controller", replaceWith = ReplaceWith("RepositoryController"))
class RepositoryControllerOld(
    private val jobProperties: JobConfig.JobProperties,
    private val vcsManager: VCSManager,
    @Qualifier("jobExecutor") private val jobExecutor: AsyncTaskExecutor
) {
    private val requestJobs = ConcurrentHashMap<String, Future<out VcsFacadeResponse>>()

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsForRelease(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get commits ({},{}] in `{}` repository", (from ?: fromDate?.toString()).orEmpty(), to, vcsPath)
        val commits = vcsManager.getCommits(vcsPath, from, fromDate, to).map { it.toOld() }
        RepositoryResponse(commits)
    }.data

    @Deprecated(
        message = "Deprecated endpoint. Does not allow usage of a git ref containing slash as commitId",
        replaceWith = ReplaceWith("getCommit(vcsPath, commitId)")
    )
    @GetMapping("commits/{commitId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitById(@PathVariable("commitId") commitId: String, @RequestParam("vcsPath") vcsPath: String) =
        getCommit(vcsPath, commitId)

    @Deprecated(
        message = "Deprecated endpoint. Backward compatibility with client version 2.0.17 and below",
        replaceWith = ReplaceWith("getCommit(vcsPath, commitId)")
    ) //NOTE: Spring boot 2 successfully maps "commit/" to "commit", but Spring boot 3 does not!
    @GetMapping("commit/", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitWithSlash(@RequestParam("vcsPath") vcsPath: String, @RequestParam("commitId") commitIdOrRef: String) =
        getCommit(vcsPath, commitIdOrRef)

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestParam("commitId") commitIdOrRef: String
    ): CommitOld {
        log.info("Get commit {} in `{}` repository", commitIdOrRef, vcsPath)
        return vcsManager.getCommit(vcsPath, commitIdOrRef).toOld()
    }

    @GetMapping("issues", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsForReleaseIssues(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestParam("to") to: String,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Find issue keys in commits ({},{}] in `{}` repository",
            (from ?: fromDate?.toString()).orEmpty(), to, vcsPath
        )
        val issues = vcsManager.getCommits(vcsPath, from, fromDate, to)
            .map { it.toOld() }
            .flatMap { IssueKeyParser.findIssueKeys(it.message) }
            .distinct()
        RepositoryResponse(issues)
    }.data

    @GetMapping("issues/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find commits by issue key {}", issueKey)
        val commits = vcsManager.findCommits(issueKey).map { it.toOld() }
        RepositoryResponse(commits)
    }.data


    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTagsForRepository(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get tags in `{}` repository", vcsPath)
        RepositoryResponse(vcsManager.getTags(vcsPath).map { it.toOld() })
    }.data


    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequestOld,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?
    ) = processJob(requestId ?: UUID.randomUUID().toString()) {
        log.info("Search issue keys {} in specified commit ranges", searchRequest.issues)
        vcsManager.searchIssuesInRanges(searchRequest.toNew()).toOld()
    }

    @PostMapping("pull-requests")
    fun createPullRequest(
        @RequestParam("vcsPath") vcsPath: String,
        @RequestBody createPullRequest: CreatePullRequest
    ): PullRequestResponse {
        log.info(
            "Create pull request ({} -> {}) in `{}` repository",
            vcsPath,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch
        )
        return vcsManager.createPullRequest(vcsPath, createPullRequest).toOld()
    }

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
        private val log = LoggerFactory.getLogger(RepositoryControllerOld::class.java)

        @JsonIgnoreProperties(ignoreUnknown = true)
        class CommitOld @JsonCreator constructor(
            val id: String,
            val message: String,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM d, yyyy h:mm:s a", locale = "en_GB")
            val date: Date,
            val author: String,
            val parents: List<String>,
            val vcsUrl: String,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is CommitOld) return false
                if (id != other.id) return false
                if (message != other.message) return false
                if (date != other.date) return false
                if (author != other.author) return false
                if (parents != other.parents) return false
                if (vcsUrl != other.vcsUrl) return false
                return true
            }

            override fun hashCode() = Objects.hash(id, message, date, author, parents, vcsUrl)

            override fun toString(): String {
                return "CommitOld(id='$id', message='$message', date=$date, author='$author', parents=$parents, vcsUrl='$vcsUrl')"
            }
        }

        private fun Commit.toOld() = CommitOld(id, message, date, author.name, parents, repository.sshUrl)

        data class TagOld(val commitId: String, val name: String)

        private fun Tag.toOld() = TagOld(commitId, name)

        data class PullRequestResponse(val id: Long)

        private fun PullRequest.toOld() = PullRequestResponse(index)

        data class RepositoryRangeOld(val vcsPath: String, val fromCid: String?, val fromDate: Date?, val toCid: String)

        private fun RepositoryRange.toOld() = RepositoryRangeOld(sshUrl, fromCid, fromDate, toCid)

        private fun RepositoryRangeOld.toNew() = RepositoryRange(vcsPath, fromCid, fromDate, toCid)

        data class SearchIssuesInRangesRequestOld(val issues: Set<String>, val ranges: Set<RepositoryRangeOld>)

        private fun SearchIssuesInRangesRequestOld.toNew() =
            SearchIssuesInRangesRequest(issues, ranges.map { range -> range.toNew() }.toSet())

        data class SearchIssueInRangesResponseOld(val issueRanges: Map<String, Set<RepositoryRangeOld>>) :
            VcsFacadeResponse

        private fun SearchIssueInRangesResponse.toOld() =
            SearchIssueInRangesResponseOld(issueRanges.mapValues { it.value.map { range -> range.toOld() }.toSet() })
    }
}
