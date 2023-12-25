package org.octopusden.octopus.vcsfacade.service.impl

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Stack
import java.util.concurrent.TimeUnit
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.GitLabApiException
import org.gitlab4j.api.models.Project
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitlab",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GitlabService(gitLabProperties: VCSConfig.GitLabProperties) : VCSClient(gitLabProperties) {
    private val clientFunc: () -> GitLabApi = {
        val authException by lazy {
            IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
        }
        gitLabProperties.token
            ?.let { GitLabApi(gitLabProperties.host, gitLabProperties.token) }
            ?: getGitlabApi(gitLabProperties, authException).also { api ->
                api.setAuthTokenSupplier { getToken(gitLabProperties, authException) }
            }
    }

    private val client by lazy { clientFunc() }

    private var tokenObtained: Instant = Instant.MIN
    private var token: String = ""

    override val vcsPathRegex = "ssh://git@$host:(([^/]+/)+)([^/]+).git".toRegex()

    private fun String.toNamespaceAndProject() =
        vcsPathRegex.find(this.lowercase())!!.destructured.let { it.component1().trimEnd('/') to it.component3() }

    override fun getCommits(vcsPath: String, toId: String, fromId: String): Collection<Commit> {
        val project = getProject(vcsPath)
        val toIdValue = getBranchCommit(vcsPath, toId)?.id ?: toId
        getCommit(vcsPath, toIdValue)
        val commits = retryableExecution {
            client.commitsApi
                .getCommits(project.id, toIdValue, null, null, 100)
                .asSequence()
                .flatten()
                .map { c -> Commit(c.id, c.message, c.committedDate, c.authorName, c.parentIds, vcsPath) }
                .toList()
        }
        return filterCommitGraph(vcsPath, project.name, commits, fromId, null, toIdValue)
    }

    override fun getCommits(vcsPath: String, toId: String, fromDate: Date?): Collection<Commit> {
        val project = getProject(vcsPath)
        val toIdValue = getBranchCommit(vcsPath, toId)?.id ?: toId
        getCommit(vcsPath, toIdValue)
        val commits = retryableExecution {
            client.commitsApi
                .getCommits(project.id, toIdValue, null, null, 100)
                .asSequence()
                .flatten()
                .map { c -> Commit(c.id, c.message, c.committedDate, c.authorName, c.parentIds, vcsPath) }
                .toList()
        }

        return filterCommitGraph(vcsPath, project.name, commits, null, fromDate, toIdValue)
    }

    override fun getCommits(issueKey: String) = emptyList<Commit>()

    override fun getTags(vcsPath: String): List<Tag> {
        val project = getProject(vcsPath)
        return retryableExecution {
            client.tagsApi
                .getTags(project.id)
                .asSequence()
                .map { Tag(it.commit.id, it.name) }
                .toList()
        }
    }

    override fun getCommit(vcsPath: String, commitIdOrRef: String): Commit {
        val project = getProject(vcsPath)
        return retryableExecution("Commit '$commitIdOrRef' does not exist in repository '${project.name}'.") {
            val commit = client.commitsApi
                .getCommit(project.id, commitIdOrRef)
            Commit(commit.id, commit.message, commit.committedDate, commit.authorName, commit.parentIds, vcsPath)
        }
    }

    override fun createPullRequest(
        vcsPath: String,
        pullRequestRequest: PullRequestRequest
    ): PullRequestResponse {
        val project = getProject(vcsPath)
        val (namespace, projectName) = vcsPath.toNamespaceAndProject()
        val sourceBranch = pullRequestRequest.sourceBranch.toShortBranchName()
        val targetBranch = pullRequestRequest.targetBranch.toShortBranchName()
        retryableExecution("Source branch 'absent' not found in '$namespace:$projectName'") {
            client.repositoryApi.getBranch(project.id, sourceBranch)
        }
        retryableExecution("Target branch 'absent' not found in '$namespace:$projectName'") {
            client.repositoryApi.getBranch(project.id, targetBranch)
        }
        val mergeRequest = retryableExecution {
            client.mergeRequestApi.createMergeRequest(
                project.id,
                sourceBranch,
                targetBranch,
                pullRequestRequest.title,
                pullRequestRequest.description,
                0L
            )
        }
        return PullRequestResponse(mergeRequest.id)
    }

    private fun getBranchCommit(vcsPath: String, branch: String): org.gitlab4j.api.models.Commit? {
        val shortBranchName = branch.toShortBranchName()
        val project = getProject(vcsPath)
        return retryableExecution {
            client.repositoryApi.getBranches(project, shortBranchName)
                .firstOrNull { b -> b.name == shortBranchName }?.commit
        }
    }

    private fun getProject(vcsPath: String): Project {
        val (namespace, project) = vcsPath.toNamespaceAndProject()
        retryableExecution("Project $namespace does not exist.") { client.groupApi.getGroup(namespace) }
        return retryableExecution("Repository $namespace/$project does not exist.") {
            client.projectApi.getProject(namespace, project)
        }
    }

    private fun <T> retryableExecution(
        message: String = "",
        attemptLimit: Int = 3,
        attemptIntervalSec: Long = 3,
        func: () -> T
    ): T {
        lateinit var latestException: Exception
        for (attempt in 1..attemptLimit) {
            try {
                return func()
            } catch (e: GitLabApiException) {
                if (e.httpStatus == 404) {
                    throw NotFoundException(message)
                }
                log.error("${e.message}, attempt=$attempt:$attemptLimit, retry in $attemptIntervalSec sec")
                latestException = e
                TimeUnit.SECONDS.sleep(attemptIntervalSec)
            }
        }
        throw IllegalStateException(latestException.message)
    }

    private fun getGitlabApi(
        gitLabProperties: VCSConfig.GitLabProperties,
        authException: IllegalStateException
    ) = GitLabApi.oauth2Login(
        gitLabProperties.host,
        gitLabProperties.username ?: throw authException,
        gitLabProperties.password ?: throw authException
    ).also { api ->
        tokenObtained = Instant.now()
        token = api.authToken
    }

    private fun getToken(
        gitLabProperties: VCSConfig.GitLabProperties,
        authException: IllegalStateException
    ): String {
        if (tokenObtained.isBefore(Instant.now().minus(Duration.ofMinutes(110)))) {
            log.info("Refresh auth token")
            getGitlabApi(gitLabProperties, authException)
        }
        return token
    }

    private fun filterCommitGraph(
        vcsPath: String,
        project: String,
        commits: Collection<Commit>,
        fromId: String?,
        fromDate: Date?,
        toIdValue: String
    ): List<Commit> {
        val graph = commits.map { commit -> commit.id to commit }.toMap()
        if (log.isTraceEnabled) {
            log.trace("Graph has ${graph.size} items: $graph")
        } else {
            log.debug("Graph size=${graph.size}")
        }

        val releasedCommits = fromId?.let { fromIdValue ->
            val exceptionFunction: (commitId: String) -> NotFoundException = { commit ->
                getCommit(vcsPath, fromIdValue)
                NotFoundException("Can't find commit '$commit' in graph but it exists in the '$vcsPath'")
            }
            graph.findReleasedCommits(fromIdValue, exceptionFunction)
        } ?: emptySet()

        val rootCommit = graph[toIdValue]
            ?: throw NotFoundException("Commit '$toIdValue' does not exist in repository '$project'.")

        // Classical dfs to find all commits that should be passed to release
        val stack = Stack<Commit>().also { it.push(rootCommit) }
        val visited = mutableSetOf<Commit>()
        while (stack.isNotEmpty()) {
            val currentCommit = stack.pop()
            visited += currentCommit
            currentCommit.parents
                .map { graph[it]!! }
                .filter { it !in visited && it !in releasedCommits }
                .forEach { stack.add(it) }
        }

        val filter = fromId?.let { _ ->
            { true }
        }
            ?: fromDate?.let { fromDateValue -> { c: Commit -> c.date > fromDateValue } }
            ?: { true }

        return visited.filter(filter).sortedByDescending { it.date }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GitlabService::class.java)

        private fun String.toShortBranchName() = this.replace("^refs/heads/".toRegex(), "")

        private fun Map<String, Commit>.findReleasedCommits(
            lastReleaseId: String,
            errorFunction: (commitId: String) -> Exception
        ): Set<Commit> {
            val releaseCommit = get(lastReleaseId) ?: throw errorFunction(lastReleaseId)
            val visited = mutableSetOf<Commit>()
            val stack = Stack<Commit>().also { it.push(releaseCommit) }
            while (stack.isNotEmpty()) {
                val currentCommit = stack.pop()
                visited += currentCommit
                currentCommit.parents.map { get(it) }.filter { it !in visited }.forEach { stack.push(it) }
            }
            return visited
        }
    }
}
