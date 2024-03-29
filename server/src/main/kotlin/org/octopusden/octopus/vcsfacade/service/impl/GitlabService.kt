package org.octopusden.octopus.vcsfacade.service.impl

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Stack
import java.util.concurrent.TimeUnit
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.GitLabApiException
import org.gitlab4j.api.models.AbstractUser
import org.gitlab4j.api.models.MergeRequest
import org.gitlab4j.api.models.Project
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.gitlab4j.api.models.Branch as GitlabBranch
import org.gitlab4j.api.models.Commit as GitlabCommit
import org.gitlab4j.api.models.Tag as GitlabTag

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitlab", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GitlabService(
    gitLabProperties: VCSConfig.GitLabProperties
) : VCSClient(gitLabProperties, VcsServiceType.GITLAB) {
    private val clientFunc: () -> GitLabApi = {
        val authException by lazy {
            IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
        }
        gitLabProperties.token?.let { GitLabApi(httpUrl, gitLabProperties.token) } ?: getGitlabApi(
            gitLabProperties,
            authException
        ).also { api ->
            api.setAuthTokenSupplier { getToken(gitLabProperties, authException) }
        }
    }

    private val client by lazy { clientFunc() }

    private var tokenObtained: Instant = Instant.MIN
    private var token: String = ""

    override val sshUrlRegex = "(?:ssh://)?git@$host:((?:[^/]+/)+)([^/]+).git".toRegex()

    override fun getSshUrl(group: String, repository: String) = "ssh://git@$host:$group/$repository.git"

    override fun getBranches(group: String, repository: String) = retryableExecution {
        client.repositoryApi.getBranches(getProject(group, repository).id).map { it.toBranch(group, repository) }
    }

    override fun getTags(group: String, repository: String) = retryableExecution {
        client.tagsApi.getTags(getProject(group, repository).id).map { it.toTag(group, repository) }
    }

    override fun getCommits(group: String, repository: String, toId: String, fromId: String): Collection<Commit> {
        val project = getProject(group, repository)
        val toIdValue = getCommitByBranchOrId(project, toId).id
        val commits = retryableExecution {
            client.commitsApi.getCommits(project.id, toIdValue, null, null, 100).asSequence().flatten()
                .map { it.toCommit(group, repository) }.toList()
        }
        return filterCommitGraph(group, repository, commits, fromId, null, toIdValue)
    }

    override fun getCommits(group: String, repository: String, toId: String, fromDate: Date?): Collection<Commit> {
        val project = getProject(group, repository)
        val toIdValue = getCommitByBranchOrId(project, toId).id
        val commits = retryableExecution {
            client.commitsApi.getCommits(project.id, toIdValue, null, null, 100).asSequence().flatten()
                .map { it.toCommit(group, repository) }.toList()
        }
        return filterCommitGraph(group, repository, commits, null, fromDate, toIdValue)
    }

    override fun getCommit(group: String, repository: String, id: String) =
        getCommitByBranchOrId(getProject(group, repository), id).toCommit(group, repository)


    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        val project = getProject(group, repository)
        val sourceBranch = createPullRequest.sourceBranch.toShortBranchName()
        val targetBranch = createPullRequest.targetBranch.toShortBranchName()
        retryableExecution("Source branch 'absent' not found in '$group:$repository'") {
            client.repositoryApi.getBranch(project.id, sourceBranch)
        }
        retryableExecution("Target branch 'absent' not found in '$group:$repository'") {
            client.repositoryApi.getBranch(project.id, targetBranch)
        }
        return retryableExecution {
            client.mergeRequestApi.createMergeRequest(
                project.id, sourceBranch, targetBranch, createPullRequest.title, createPullRequest.description, 0L
            )
        }.toPullRequest(group, repository)
    }

    override fun getPullRequest(group: String, repository: String, index: Long) = retryableExecution {
        client.mergeRequestApi.getMergeRequestApprovals(
            getProject(group, repository).id, index
        )
    }.toPullRequest(group, repository)

    override fun findCommits(group: String, repository: String, ids: Set<String>) = ids.mapNotNull {
        try {
            getCommit(group, repository, it)
        } catch (e: NotFoundException) {
            null
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>) = indexes.mapNotNull {
        try {
            getPullRequest(group, repository, it)
        } catch (e: NotFoundException) {
            null
        }
    }

    override fun findBranches(issueKey: String): List<Branch> {
        log.warn("There is no native implementation of findBranches for $vcsServiceType")
        return emptyList()
    }

    override fun findCommits(issueKey: String): List<Commit> {
        log.warn("There is no native implementation of findCommits for $vcsServiceType")
        return emptyList()
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests for $vcsServiceType")
        return emptyList()
    }

    private fun getCommitByBranchOrId(project: Project, branchOrId: String): GitlabCommit {
        val shortBranchName = branchOrId.toShortBranchName()
        val id = retryableExecution {
            client.repositoryApi.getBranches(project, shortBranchName)
                .firstOrNull { b -> b.name == shortBranchName }?.commit?.id
        } ?: branchOrId
        return retryableExecution("Commit '$id' does not exist in repository '${project.namespace.path}:${project.name}'.") {
            client.commitsApi.getCommit(project.id, id)
        }
    }

    private fun getProject(namespace: String, project: String): Project {
        retryableExecution("Group '$namespace' does not exist.") { client.groupApi.getGroup(namespace) }
        return retryableExecution("Repository '$namespace:$project' does not exist.") {
            client.projectApi.getProject(namespace, project)
        }
    }

    private fun getRepository(namespace: String, project: String) = Repository(
        getSshUrl(namespace, project), "$httpUrl/$namespace/$project"
    )

    private fun GitlabBranch.toBranch(namespace: String, project: String) = Branch(
        name, commit.id, "$httpUrl/$namespace/$project/-/tree/$name?ref_type=heads", getRepository(namespace, project)
    )

    private fun GitlabTag.toTag(namespace: String, project: String) = Tag(
        name, commit.id, "$httpUrl/$namespace/$project/-/tree/$name?ref_type=tags", getRepository(namespace, project)
    )

    private fun <T : AbstractUser<T>> AbstractUser<T>.toUser() = User(username, avatarUrl)

    private fun GitlabCommit.toCommit(namespace: String, project: String) = Commit(
        id,
        message,
        committedDate,
        author?.toUser() ?: User(authorName),
        parentIds,
        "$httpUrl/$namespace/$project/-/commit/$id",
        getRepository(namespace, project)
    )

    private fun MergeRequest.toPullRequest(namespace: String, project: String) = PullRequest(
        id,
        title,
        description,
        author.toUser(),
        sourceBranch,
        targetBranch,
        assignees.map { it.toUser() },
        reviewers.map { reviewer ->
            PullRequestReviewer(reviewer.toUser(), this.approvedBy.find { it.id == reviewer.id } != null)
        },
        when (state) {
            "merged" -> PullRequestStatus.MERGED
            "closed" -> PullRequestStatus.DECLINED
            else -> PullRequestStatus.OPENED
        },
        createdAt,
        updatedAt,
        "$httpUrl/$namespace/$project/-/merge_requests/$id",
        getRepository(namespace, project)
    )

    private fun <T> retryableExecution(
        message: String = "", attemptLimit: Int = 3, attemptIntervalSec: Long = 3, func: () -> T
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
        gitLabProperties: VCSConfig.GitLabProperties, authException: IllegalStateException
    ) = GitLabApi.oauth2Login(
        gitLabProperties.host,
        gitLabProperties.username ?: throw authException,
        gitLabProperties.password ?: throw authException
    ).also { api ->
        tokenObtained = Instant.now()
        token = api.authToken
    }

    private fun getToken(
        gitLabProperties: VCSConfig.GitLabProperties, authException: IllegalStateException
    ): String {
        if (tokenObtained.isBefore(Instant.now().minus(Duration.ofMinutes(110)))) {
            log.info("Refresh auth token")
            getGitlabApi(gitLabProperties, authException)
        }
        return token
    }

    private fun filterCommitGraph(
        namespace: String,
        project: String,
        commits: Collection<Commit>,
        fromId: String?,
        fromDate: Date?,
        toIdValue: String
    ): List<Commit> {
        val graph = commits.map { commit -> commit.id to commit }.toMap()
        if (log.isTraceEnabled) {
            log.trace("Graph has {} items: {}", graph.size, graph)
        } else {
            log.debug("Graph size={}", graph.size)
        }
        val releasedCommits = fromId?.let { fromIdValue ->
            val exceptionFunction: (commitId: String) -> NotFoundException = { commit ->
                getCommit(namespace, project, fromIdValue)
                NotFoundException("Can't find commit '$commit' in graph but it exists in the '$namespace:$project'")
            }
            graph.findReleasedCommits(fromIdValue, exceptionFunction)
        } ?: emptySet()
        val rootCommit = graph[toIdValue]
            ?: throw NotFoundException("Commit '$toIdValue' does not exist in repository '$namespace:$project'.")
        // Classical dfs to find all commits that should be passed to release
        val stack = Stack<Commit>().also { it.push(rootCommit) }
        val visited = mutableSetOf<Commit>()
        while (stack.isNotEmpty()) {
            val currentCommit = stack.pop()
            visited += currentCommit
            currentCommit.parents.map { graph[it]!! }.filter { it !in visited && it !in releasedCommits }
                .forEach { stack.add(it) }
        }
        val filter = fromId?.let { _ ->
            { true }
        } ?: fromDate?.let { fromDateValue -> { c: Commit -> c.date > fromDateValue } } ?: { true }
        return visited.filter(filter).sortedByDescending { it.date }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GitlabService::class.java)

        private fun String.toShortBranchName() = replace("^refs/heads/".toRegex(), "")

        private fun Map<String, Commit>.findReleasedCommits(
            lastReleaseId: String, errorFunction: (commitId: String) -> Exception
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
