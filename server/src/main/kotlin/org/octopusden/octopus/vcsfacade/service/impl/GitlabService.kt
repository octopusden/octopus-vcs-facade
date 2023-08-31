package org.octopusden.octopus.vcsfacade.service.impl

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
import java.util.Date
import java.util.Stack

@Service
@ConditionalOnProperty(prefix = "gitlab", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class GitlabService(gitLabProperties: VCSConfig.GitLabProperties) : VCSClient(gitLabProperties) {

    override val repoPrefix: String = "git@"

    private val gitlabApiFunc: () -> GitLabApi = {
        val authException by lazy {
            IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
        }
        gitLabProperties.token
            ?.let { GitLabApi(gitLabProperties.host, gitLabProperties.token) }
            ?: GitLabApi.oauth2Login(
                gitLabProperties.host,
                gitLabProperties.username ?: throw authException,
                gitLabProperties.password ?: throw authException
            )
    }

    private val gitLabApi by lazy { gitlabApiFunc() }

    /**
     * fromId and fromDate are not works together, must be specified one of it or not one
     */
    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        validateParams(fromId, fromDate)
        val project = getProject(vcsPath)
        val toIdValue = getBranchCommit(vcsPath, toId)?.id ?: toId
        getCommit(vcsPath, toIdValue)

        val commits = gitLabApi.commitsApi
            .getCommits(project.id, toIdValue, null, null, 100)
            .asSequence()
            .flatten()
            .map { commit -> Commit(commit.id, commit.message, commit.committedDate, commit.authorName, commit.parentIds, vcsPath) }
            .toList()

        val graph = buildGraph(commits)
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
        }?: emptySet()

        val rootCommit = graph[toIdValue]
            ?: throw NotFoundException("Commit '$toIdValue' does not exist in repository '${project.name}'.")

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
            ?: fromDate?.let { fromDateValue -> { c: Commit -> c.date >= fromDateValue } }
            ?: { true }

        return visited.filter(filter)
            .toList()
    }

    override fun getCommits(issueKey: String) = emptyList<Commit>()

    override fun getTags(vcsPath: String): List<Tag> {
        val project = getProject(vcsPath)
        return gitLabApi.tagsApi.getTags(project.id)
                .asSequence()
                .map { Tag(it.commit.id, it.name) }
                .toList()
    }

    override fun getCommit(vcsPath: String, commitId: String): Commit {
        val project = getProject(vcsPath)
        return execute("Commit '$commitId' does not exist in repository '${project.name}'.") {
            val commit = gitLabApi.commitsApi
                .getCommit(project.id, commitId)
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

        execute("Source branch 'absent' not found in '$namespace:$projectName'") { gitLabApi.repositoryApi.getBranch(project.id, sourceBranch) }
        execute("Target branch 'absent' not found in '$namespace:$projectName'") { gitLabApi.repositoryApi.getBranch(project.id, targetBranch) }

        val mergeRequest = gitLabApi.mergeRequestApi.createMergeRequest(
            project.id,
            sourceBranch,
            targetBranch,
            pullRequestRequest.title,
            pullRequestRequest.description,
            0L
        )
        return PullRequestResponse(mergeRequest.id)
    }

    private fun getBranchCommit(vcsPath: String, branch: String) : org.gitlab4j.api.models.Commit? {
        val shortBranchName = branch.toShortBranchName()
        val project = getProject(vcsPath)
        return gitLabApi.repositoryApi.getBranches(project, shortBranchName).firstOrNull { b -> b.name == shortBranchName }?.commit
    }

    private fun getProject(vcsPath: String): Project {
        val (namespace, project) = vcsPath.toNamespaceAndProject()
        execute("Project $namespace does not exist.") { gitLabApi.groupApi.getGroup(namespace) }
        return execute("Repository $namespace/$project does not exist.") {
            gitLabApi.projectApi.getProject(namespace, project)
        }
    }

    private  fun <T> execute(message: String, func: () -> T ): T {
        try {
            return func()
        } catch (e: GitLabApiException) {
            if (e.httpStatus == 404) {
                throw NotFoundException(message)
            }
            throw IllegalStateException(e.message)
        }
    }

    private fun buildGraph(commits: List<Commit>) = commits.map { commit -> commit.id to commit }.toMap()

    private fun CommitGraph.findReleasedCommits(
        lastReleaseId: String,
        errorFunction: (commitId: String) -> Exception
    ): Set<Commit> {
        val releaseCommit = get(lastReleaseId) ?: throw errorFunction.invoke(lastReleaseId)
        val visited = mutableSetOf<Commit>()
        val stack = Stack<Commit>().also { it.push(releaseCommit) }
        while (stack.isNotEmpty()) {
            val currentCommit = stack.pop()
            visited += currentCommit
            currentCommit.parents.map { get(it)!! }.filter { it !in visited }.forEach { stack.push(it) }
        }
        return visited
    }
    private fun String.toShortBranchName() = this.replace("^refs/heads/".toRegex(), "")

    companion object {
        private val log = LoggerFactory.getLogger(GitlabService::class.java)
    }
}

private typealias CommitGraph = Map<String, Commit>

fun String.toNamespaceAndProject() = split(":").last()
    .replace("\\.git$".toRegex(), "")
    .let {
        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
    }
