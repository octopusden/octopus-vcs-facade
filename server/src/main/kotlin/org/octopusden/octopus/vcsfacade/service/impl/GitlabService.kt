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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnProperty(prefix = "vcs-facade.vcs.gitlab", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class GitlabService(gitLabProperties: VCSConfig.GitLabProperties) : VCSClient(gitLabProperties) {

    override val repoPrefix: String = "git@"

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

    /**
     * fromId and fromDate are not works together, must be specified one of it or not one
     */
    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit> {
        validateParams(fromId, fromDate)
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

        return filterCommitGraph(vcsPath, project.name, commits, fromId, fromDate, toIdValue)
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

    override fun getCommit(vcsPath: String, commitId: String): Commit {
        val project = getProject(vcsPath)
        return retryableExecution("Commit '$commitId' does not exist in repository '${project.name}'.") {
            val commit = client.commitsApi
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

        retryableExecution("Source branch 'absent' not found in '$namespace:$projectName'") { client.repositoryApi.getBranch(project.id, sourceBranch) }
        retryableExecution("Target branch 'absent' not found in '$namespace:$projectName'") { client.repositoryApi.getBranch(project.id, targetBranch) }

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

    override fun getLog(): Logger = log

    private fun getBranchCommit(vcsPath: String, branch: String) : org.gitlab4j.api.models.Commit? {
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

    private fun String.toShortBranchName() = this.replace("^refs/heads/".toRegex(), "")

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

    companion object {
        private val log = LoggerFactory.getLogger(GitlabService::class.java)
    }
}

fun String.toNamespaceAndProject() = split(":").last()
    .replace("\\.git$".toRegex(), "")
    .let {
        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
    }
