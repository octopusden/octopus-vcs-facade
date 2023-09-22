package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.slf4j.Logger
import java.util.Date
import java.util.Locale
import java.util.Stack

abstract class VCSClient(vcsProperties: VCSConfig.VCSProperties) {
    private val basePath: String = vcsProperties.host
    protected abstract val repoPrefix: String

    fun isSupport(vcsPath: String): Boolean {
        return vcsPath.lowercase(Locale.getDefault()).startsWith("$repoPrefix${getHost()}")
    }

    protected fun getHost() = basePath.replace(Regex("^(https|http)://"), "")
    protected fun validateParams(fromId: String?, fromDate: Date?) {
        fromId?.let { _ ->
            fromDate?.let { _ ->
                throw ArgumentsNotCompatibleException("Params 'fromId' and 'fromDate' can not be used together")
            }
        }
    }

    abstract fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit>
    abstract fun getCommits(issueKey: String): List<Commit>
    abstract fun getTags(vcsPath: String): List<Tag>
    abstract fun getCommit(vcsPath: String, commitIdOrRef: String): Commit
    abstract fun createPullRequest(
        vcsPath: String,
        pullRequestRequest: PullRequestRequest
    ): PullRequestResponse

    protected abstract fun getLog(): Logger

    protected fun filterCommitGraph(
        vcsPath: String,
        project: String,
        commits: Collection<Commit>,
        fromId: String?,
        fromDate: Date?,
        toIdValue: String
    ): List<Commit> {
        val graph = commits.map { commit -> commit.id to commit }.toMap()
        if (getLog().isTraceEnabled) {
            getLog().trace("Graph has ${graph.size} items: $graph")
        } else {
            getLog().debug("Graph size=${graph.size}")
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
            ?: fromDate?.let { fromDateValue -> { c: Commit -> c.date >= fromDateValue } }
            ?: { true }

        return visited.filter(filter)
            .toList()
    }

    private fun CommitGraph.findReleasedCommits(
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

private typealias CommitGraph = Map<String, Commit>
