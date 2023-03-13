package org.octopusden.vcsfacade.service.impl

import org.octopusden.vcsfacade.client.common.dto.Commit
import org.octopusden.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.vcsfacade.client.common.dto.Tag
import org.octopusden.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.vcsfacade.config.VCSConfig
import org.octopusden.vcsfacade.service.VCSClient
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.GitLabApiException
import org.gitlab4j.api.models.Project
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Date
import java.util.Stack

@Service
@ConditionalOnProperty(prefix = "gitlab", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class GitlabClient(gitLabProperties: VCSConfig.GitLabProperties) : VCSClient(gitLabProperties) {

    override val repoPrefix: String = "git@"
    private val gitLabApi: GitLabApi = GitLabApi(gitLabProperties.host, gitLabProperties.token)

    /**
     * fromId and fromDate are not works together, must be specified one of it or not one
     */
    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        validateParams(fromId, fromDate)
        val project = getProject(vcsPath)
        val commits = gitLabApi.commitsApi
            .getCommits(project.id, toId, null, null, 100)
            .asSequence()
            .flatten()
            .map { Commit(it.id, it.message, it.committedDate, it.authorName, it.parentIds, vcsPath) }
            .toList()

        val graph = buildGraph(commits)
        if (log.isTraceEnabled) {
            log.trace("Graph has ${graph.size} items: $graph")
        } else {
            log.debug("Graph size=${graph.size}")
        }
        val releasedCommits = fromId?.let {
            val exceptionFunction: (commitId: String) -> NotFoundException = { commit ->
                getCommit(vcsPath, it)
                NotFoundException("Can't find commit '$commit' in graph but it exists in the '$vcsPath'")
            }
            graph.findReleasedCommits(it, exceptionFunction)
        }
            ?: emptySet()
        val rootCommit = graph[toId]
            ?: throw NotFoundException("Can't find commit '$toId' in '$vcsPath'")

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
        return try {
            val commit = gitLabApi.commitsApi
                    .getCommit(project.id, commitId)
            Commit(commit.id, commit.message, commit.committedDate, commit.authorName, commit.parentIds, vcsPath)
        } catch (e: GitLabApiException) {
            if (e.httpStatus == 404) {
                throw NotFoundException("Can't find commit '$commitId' in '$vcsPath'")
            }

            throw IllegalStateException(e.message)
        }
    }

    override fun createPullRequest(
        vcsPath: String,
        pullRequestRequest: PullRequestRequest
    ): PullRequestResponse {
        throw UnsupportedOperationException("Gitlab client does not support Pull Request Creation")
    }

    private fun getProject(vcsPath: String): Project {
        try {
            return vcsPath.toNamespaceAndProject().let { (namespace, project) ->
                gitLabApi.projectApi.getProject(namespace, project)
            }
        } catch (e: GitLabApiException) {
            if (e.httpStatus == 404) {
                throw NotFoundException("Repository '$vcsPath' is not found")
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

    companion object {
        private val log = LoggerFactory.getLogger(GitlabClient::class.java)
    }
}

private typealias CommitGraph = Map<String, Commit>

fun String.toNamespaceAndProject() = split(":").last()
    .replace("\\.git$".toRegex(), "")
    .let {
        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
    }
