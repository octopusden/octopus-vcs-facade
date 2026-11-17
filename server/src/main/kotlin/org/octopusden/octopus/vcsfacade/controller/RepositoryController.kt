package org.octopusden.octopus.vcsfacade.controller

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.dto.RepositoryResponse
import org.octopusden.octopus.vcsfacade.issue.IssueKeyParser
import org.octopusden.octopus.vcsfacade.service.RequestProcessor
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.slf4j.LoggerFactory
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
import java.util.Date
import java.util.UUID

@RestController
@RequestMapping("rest/api/2/repository")
class RepositoryController(
    private val vcsManager: VcsManager,
    private val requestProcessor: RequestProcessor,
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRepository(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get repository {}", sshUrl)
        vcsManager.getRepository(sshUrl)
    }

    @GetMapping("commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommits(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("fromHashOrRef", required = false) fromHashOrRef: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestParam("toHashOrRef") toHashOrRef: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Get commits ({},{}] in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            sshUrl,
        )
        RepositoryResponse(vcsManager.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef).toList())
    }.data.sorted()

    @GetMapping("commits/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitsWithFiles(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("fromHashOrRef", required = false) fromHashOrRef: String?,
        @RequestParam("fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date?,
        @RequestParam("toHashOrRef") toHashOrRef: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Get commits ({},{}] with files (limit {}) in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            commitFilesLimit,
            sshUrl,
        )
        RepositoryResponse(
            vcsManager
                .getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef)
                .toList()
                .map { it.mapFilesList(commitFilesLimit) },
        )
    }.data.sorted()

    @GetMapping("commit", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommit(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("hashOrRef") hashOrRef: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get commit {} in {} repository", hashOrRef, sshUrl)
        vcsManager.getCommit(sshUrl, hashOrRef)
    }

    @GetMapping("commit/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getCommitWithFiles(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("hashOrRef") hashOrRef: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
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
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Find issue keys in commits ({},{}] in {} repository",
            (fromHashOrRef ?: fromDate?.toString()).orEmpty(),
            toHashOrRef,
            sshUrl,
        )
        RepositoryResponse(
            vcsManager
                .getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)
                .toList()
                .flatMap { IssueKeyParser.findIssueKeys(it.message) }
                .distinct(),
        )
    }.data.sorted()

    @GetMapping("tags", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTags(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("names", required = false) names: Set<String>?,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get tags{} in {} repository", names?.joinToString(prefix = " with names {", postfix = "}") ?: "", sshUrl)
        RepositoryResponse(vcsManager.getTags(sshUrl, names).toList())
    }.data.sorted()

    @PostMapping("tags", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestBody createTag: CreateTag,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Create tag {} on {} in {} repository", createTag.name, createTag.hashOrRef, sshUrl)
        vcsManager.createTag(sshUrl, createTag)
    }

    @GetMapping("tag", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("name") name: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get tag {} in {} repository", name, sshUrl)
        vcsManager.getTag(sshUrl, name)
    }

    @DeleteMapping("tag", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deleteTag(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("name") name: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Delete tag {} in {} repository", name, sshUrl)
        vcsManager.deleteTag(sshUrl, name)
    }

    @PostMapping(
        "search-issues-in-ranges",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun searchIssuesInRanges(
        @RequestBody searchRequest: SearchIssuesInRangesRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Search issue keys {} in specified commit ranges", searchRequest.issueKeys)
        vcsManager.searchIssuesInRanges(searchRequest)
    }

    @PostMapping(
        "pull-requests",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun createPullRequest(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestBody createPullRequest: CreatePullRequest,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info(
            "Create pull request ({} -> {}) in {} repository",
            sshUrl,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch,
        )
        vcsManager.createPullRequest(sshUrl, createPullRequest)
    }

    @GetMapping("find", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findByIssueKeys(
        @RequestParam("issueKeys") issueKeys: Set<String>,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Get search summary for issue keys {}", issueKeys)
        vcsManager.find(issueKeys)
    }

    @GetMapping("branches/find", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findBranchesByIssueKeys(
        @RequestParam("issueKeys") issueKeys: Set<String>,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find branches by issue keys {}", issueKeys)
        RepositoryResponse(vcsManager.findBranches(issueKeys).toList())
    }.data.sorted()

    @GetMapping("commits/find", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsByIssueKeys(
        @RequestParam("issueKeys") issueKeys: Set<String>,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find commits by issue keys {}", issueKeys)
        RepositoryResponse(vcsManager.findCommits(issueKeys).toList())
    }.data.sorted()

    @GetMapping("commits/files/find", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsWithFilesByIssueKeys(
        @RequestParam("issueKeys") issueKeys: Set<String>,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find commits with files (limit {}) by issue keys {}", commitFilesLimit, issueKeys)
        RepositoryResponse(
            vcsManager.findCommitsWithFiles(issueKeys).toList().map { it.mapFilesList(commitFilesLimit) },
        )
    }.data.sorted()

    @GetMapping("pull-requests/find", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPullRequestsByIssueKeys(
        @RequestParam("issueKeys") issueKeys: Set<String>,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.info("Find pull requests by issue keys {}", issueKeys)
        RepositoryResponse(vcsManager.findPullRequests(issueKeys).toList())
    }.data.sorted()

    @Deprecated(
        message = "Deprecated endpoint",
        replaceWith = ReplaceWith("findByIssueKeys(listOf(issueKey), requestId)"),
    )
    @GetMapping("find/{issueKey}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Get search summary for issue key {}", issueKey)
        vcsManager.find(setOf(issueKey))
    }

    @Deprecated(
        message = "Deprecated endpoint",
        replaceWith = ReplaceWith("findBranchesByIssueKeys(listOf(issueKey), requestId)"),
    )
    @GetMapping("find/{issueKey}/branches", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findBranchesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find branches by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findBranches(setOf(issueKey)).toList())
    }.data.sorted()

    @Deprecated(
        message = "Deprecated endpoint",
        replaceWith = ReplaceWith("findCommitsByIssueKeys(listOf(issueKey), requestId)"),
    )
    @GetMapping("find/{issueKey}/commits", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find commits by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findCommits(setOf(issueKey)).toList())
    }.data.sorted()

    @Deprecated(
        message = "Deprecated endpoint",
        replaceWith = ReplaceWith("findCommitsWithFilesByIssueKeys(listOf(issueKey), commitFilesLimit, requestId)"),
    )
    @GetMapping("find/{issueKey}/commits/files", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findCommitsWithFilesByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestParam("commitFilesLimit", defaultValue = "0") commitFilesLimit: Int,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find commits with files (limit {}) by issue key {}", commitFilesLimit, issueKey)
        RepositoryResponse(
            vcsManager.findCommitsWithFiles(setOf(issueKey)).toList().map { it.mapFilesList(commitFilesLimit) },
        )
    }.data.sorted()

    @Deprecated(
        message = "Deprecated endpoint",
        replaceWith = ReplaceWith("findPullRequestsByIssueKeys(listOf(issueKey), requestId)"),
    )
    @GetMapping("find/{issueKey}/pull-requests", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findPullRequestsByIssueKey(
        @PathVariable("issueKey") issueKey: String,
        @RequestHeader(Constants.DEFERRED_RESULT_HEADER, required = false) requestId: String?,
    ) = processRequest(requestId ?: UUID.randomUUID().toString()) {
        log.warn("Deprecated call! Find pull requests by issue key {}", issueKey)
        RepositoryResponse(vcsManager.findPullRequests(setOf(issueKey)).toList())
    }.data.sorted()

    private inline fun <reified T : Any> processRequest(
        requestId: String,
        noinline func: () -> T,
    ): T = requestProcessor.process(requestId, jacksonTypeRef<T>().type, func)

    companion object {
        private val log = LoggerFactory.getLogger(RepositoryController::class.java)

        private fun CommitWithFiles.mapFilesList(commitFilesLimit: Int) =
            files.sorted().let {
                CommitWithFiles(commit, totalFiles, if (commitFilesLimit > 0) it.take(commitFilesLimit) else it)
            }
    }
}
