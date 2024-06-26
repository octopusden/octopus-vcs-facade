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
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VcsConfig
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate.DateValue
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate.HashOrRefValue
import org.octopusden.octopus.vcsfacade.service.VcsService
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
@Deprecated("Not used")
class GitlabService(
    gitlabProperties: VcsConfig.GitlabProperties
) : VcsService(gitlabProperties) {
    private val clientFunc: () -> GitLabApi = {
        val authException by lazy {
            IllegalStateException("Auth Token or username/password must be specified for Gitlab access")
        }
        gitlabProperties.token?.let { GitLabApi(httpUrl, gitlabProperties.token) } ?: getGitlabApi(
            gitlabProperties, authException
        ).also { api ->
            api.setAuthTokenSupplier { getToken(gitlabProperties, authException) }
        }
    }

    private val client by lazy { clientFunc() }

    private var tokenObtained: Instant = Instant.MIN
    private var token: String = ""

    override val sshUrlRegex = "(?:ssh://)?git@$host:((?:[^/]+/)+)([^/]+).git".toRegex()

    override fun getBranches(group: String, repository: String): Sequence<Branch> {
        log.trace("=> getBranches({}, {})", group, repository)
        return retryableExecution {
            client.repositoryApi.getBranches(getProject(group, repository).id).asSequence().map {
                it.toBranch(group, repository)
            }
        }.also { log.trace("<= getBranches({}, {}): {}", group, repository, it) }
    }

    override fun getTags(group: String, repository: String): Sequence<Tag> {
        log.trace("=> getTags({}, {})", group, repository)
        return retryableExecution {
            client.tagsApi.getTags(getProject(group, repository).id).asSequence().map { it.toTag(group, repository) }
        }.also { log.trace("<= getTags({}, {}): {}", group, repository, it) }
    }

    override fun createTag(group: String, repository: String, createTag: CreateTag): Tag {
        log.trace("=> createTag({}, {}, {})", group, repository, createTag)
        return retryableExecution {
            client.tagsApi.createTag(
                getProject(group, repository).id, createTag.name, createTag.hashOrRef, createTag.message, ""
            ).toTag(group, repository)
        }.also { log.trace("<= createTag({}, {}, {}): {}", group, repository, createTag, it) }
    }

    override fun getTag(group: String, repository: String, name: String): Tag {
        log.trace("=> getTag({}, {}, {})", group, repository, name)
        return retryableExecution {
            client.tagsApi.getTag(getProject(group, repository).id, name).toTag(group, repository)
        }.also { log.trace("<= getTag({}, {}, {}): {}", group, repository, name, it) }
    }

    override fun deleteTag(group: String, repository: String, name: String) {
        log.trace("=> deleteTag({}, {}, {})", group, repository, name)
        retryableExecution { client.tagsApi.deleteTag(getProject(group, repository).id, name) }
        log.trace("<= deleteTag({}, {}, {})", group, repository, name)
    }

    override fun getCommits(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, from, toHashOrRef)
        val project = getProject(group, repository)
        val toHash = getCommitByHashOrRef(project, toHashOrRef).id
        val commits = lazy {
            retryableExecution {
                client.commitsApi.getCommits(project.id, toHash, null, null, 100).asSequence().flatten()
                    .map { it.toCommit(group, repository) }.toList()
            }
        }
        return if (from is HashOrRefValue) {
            val fromHash = getCommitByHashOrRef(project, from.value).id
            if (toHash == fromHash) {
                emptySequence()
            } else {
                filterCommitGraph(group, repository, commits.value, fromHash, null, toHash).also {
                    log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, from, toHashOrRef, it)
                }.asSequence()
            }
        } else {
            val fromDate = (from as? DateValue)?.value
            filterCommitGraph(group, repository, commits.value, null, fromDate, toHash).also {
                log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, fromDate, toHashOrRef, it)
            }.asSequence()
        }
    }

    override fun getCommitsWithFiles(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<CommitWithFiles> {
        log.warn("There is no native implementation of getCommitsWithFiles")
        return getCommits(group, repository, from, toHashOrRef).map { CommitWithFiles(it, 0, emptyList()) }
    }

    override fun getCommit(group: String, repository: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {}, {})", group, repository, hashOrRef)
        return getCommitByHashOrRef(getProject(group, repository), hashOrRef).toCommit(group, repository).also {
            log.trace("<= getCommit({}, {}, {}): {}", group, repository, hashOrRef, it)
        }
    }

    override fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles {
        log.warn("There is no native implementation of getCommitWithFiles")
        return CommitWithFiles(getCommit(group, repository, hashOrRef), 0, emptyList())
    }

    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        log.trace("=> createPullRequest({}, {}, {})", group, repository, createPullRequest)
        val project = getProject(group, repository)
        val sourceBranch = createPullRequest.sourceBranch.toShortRefName()
        val targetBranch = createPullRequest.targetBranch.toShortRefName()
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
        }.toPullRequest(group, repository).also {
            log.trace("<= createPullRequest({}, {}, {}): {}", group, repository, createPullRequest, it)
        }
    }

    override fun getPullRequest(group: String, repository: String, index: Long): PullRequest {
        log.trace("=> getPullRequest({}, {}, {})", group, repository, index)
        return retryableExecution {
            client.mergeRequestApi.getMergeRequestApprovals(getProject(group, repository).id, index)
        }.toPullRequest(group, repository).also {
            log.trace("<= getPullRequest({}, {}, {}): {}", group, repository, index, it)
        }
    }

    override fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit> {
        log.trace("=> findCommits({}, {}, {})", group, repository, hashes)
        return hashes.mapNotNull {
            try {
                getCommit(group, repository, it)
            } catch (e: NotFoundException) {
                null
            }
        }.asSequence().also {
            log.trace("<= findCommits({}, {}, {}): {}", group, repository, hashes, it)
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest> {
        log.trace("=> findPullRequests({}, {}, {})", group, repository, indexes)
        return indexes.mapNotNull {
            try {
                getPullRequest(group, repository, it)
            } catch (e: NotFoundException) {
                null
            }
        }.asSequence().also {
            log.trace("<= findPullRequests({}, {}, {}): {}", group, repository, indexes, it)
        }
    }

    override fun findBranches(issueKey: String): Sequence<Branch> {
        log.warn("The is no native implementation of findBranches")
        return emptySequence()
    }

    override fun findCommits(issueKey: String): Sequence<Commit> {
        log.warn("There is no native implementation of findCommits")
        return emptySequence()
    }

    override fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles> {
        log.warn("There is no native implementation of findCommitsWithFiles")
        return emptySequence()
    }

    override fun findPullRequests(issueKey: String): Sequence<PullRequest> {
        log.warn("There is no native implementation of findPullRequests")
        return emptySequence()
    }

    private fun getCommitByHashOrRef(project: Project, hashOrRef: String): GitlabCommit {
        val shortRefName = hashOrRef.toShortRefName()
        val hash = retryableExecution {
            client.repositoryApi.getBranches(project, shortRefName)
                .firstOrNull { b -> b.name == shortRefName }?.commit?.id
        } ?: retryableExecution {
            client.tagsApi.getTags(project.id).firstOrNull { t -> t.name == shortRefName }?.commit?.id
        } ?: hashOrRef
        return retryableExecution("Commit '$hash' does not exist in repository '${project.namespace.path}:${project.name}'.") {
            client.commitsApi.getCommit(project.id, hash)
        }
    }

    private fun getProject(namespace: String, project: String): Project {
        retryableExecution("Group '$namespace' does not exist.") { client.groupApi.getGroup(namespace) }
        return retryableExecution("Repository '$namespace:$project' does not exist.") {
            client.projectApi.getProject(namespace, project)
        }
    }

    private fun getRepository(namespace: String, project: String) = Repository(
        "ssh://git@$host:$namespace/$project.git", "$httpUrl/$namespace/$project"
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
            else -> PullRequestStatus.OPEN
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
                if (e.httpStatus == 404 || e.httpStatus == 400 &&
                    e.message?.let { it.startsWith("Target ") && it.endsWith(" is invalid") } == true
                ) {
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
        gitlabProperties: VcsConfig.GitlabProperties, authException: IllegalStateException
    ) = GitLabApi.oauth2Login(
        gitlabProperties.host,
        gitlabProperties.username ?: throw authException,
        gitlabProperties.password ?: throw authException
    ).also { api ->
        tokenObtained = Instant.now()
        token = api.authToken
    }

    private fun getToken(
        gitlabProperties: VcsConfig.GitlabProperties, authException: IllegalStateException
    ): String {
        if (tokenObtained.isBefore(Instant.now().minus(Duration.ofMinutes(110)))) {
            log.info("Refresh auth token")
            getGitlabApi(gitlabProperties, authException)
        }
        return token
    }

    private fun filterCommitGraph(
        namespace: String,
        project: String,
        commits: List<Commit>,
        fromHash: String?,
        fromDate: Date?,
        toHash: String
    ): List<Commit> {
        val graph = commits.map { commit -> commit.hash to commit }.toMap()
        log.trace("Graph has {} items: {}", graph.size, graph)
        val releasedCommits = fromHash?.let { fromHashValue ->
            val exceptionFunction: (hash: String) -> NotFoundException = { commit ->
                getCommit(namespace, project, fromHashValue)
                NotFoundException("Cannot find commit '$commit' in commit graph for commit '$toHash' in '$namespace:$project'")
            }
            graph.findReleasedCommits(fromHashValue, exceptionFunction)
        } ?: emptySet()
        val rootCommit = graph[toHash]
            ?: throw NotFoundException("Commit '$toHash' does not exist in repository '$namespace:$project'.")
        // Classical dfs to find all commits that should be passed to release
        val stack = Stack<Commit>().also { it.push(rootCommit) }
        val visited = mutableSetOf<Commit>()
        while (stack.isNotEmpty()) {
            val currentCommit = stack.pop()
            visited += currentCommit
            currentCommit.parents.map { graph[it]!! }.filter { it !in visited && it !in releasedCommits }
                .forEach { stack.add(it) }
        }
        val filter = fromHash?.let { _ ->
            { true }
        } ?: fromDate?.let { fromDateValue -> { c: Commit -> c.date > fromDateValue } } ?: { true }
        return visited.filter(filter)
    }

    companion object {
        private val log = LoggerFactory.getLogger(GitlabService::class.java)

        private fun String.toShortRefName() = replace("^refs/heads/".toRegex(), "")

        private fun Map<String, Commit>.findReleasedCommits(
            lastReleaseHash: String, errorFunction: (hash: String) -> Exception
        ): Set<Commit> {
            val releaseCommit = get(lastReleaseHash) ?: throw errorFunction(lastReleaseHash)
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
