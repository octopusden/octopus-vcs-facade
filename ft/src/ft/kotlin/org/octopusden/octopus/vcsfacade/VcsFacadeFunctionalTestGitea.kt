package org.octopusden.octopus.vcsfacade

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.Base64
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infrastructure.common.test.dto.NewChangeSet
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus


@EnabledIfSystemProperty(named = "test.profile", matches = "gitea")
class VcsFacadeFunctionalTestGitea : BaseVcsFacadeFunctionalTest(
    TestService.Gitea(vcsFacadeHost, vcsExternalHost, true),
    GiteaTestClient("http://$vcsHost", GITEA_USER, GITEA_PASSWORD, vcsExternalHost)
) {
    @BeforeAll
    fun beforeAllVcsFacadeFunctionalTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
        doHttpRequest(userCreationRequest(ASSIGNEE))
        doHttpRequest(userCreationRequest(REVIEWER))
        doHttpRequest(userCreationRequest(APPROVER))
    }

    @Test
    fun webhooksTestScenario() {
        val repository = "repository-2-webhooks"
        val issue = "ISSUE-10"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("VcsFacadeFunctionalTestGitea-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("$GROUP-$REPOSITORY_2.zip")!!
                        .copyTo(it)
                }
            }
        )
        doHttpRequest(webhookCreationRequest(GROUP, repository))
        doHttpRequest(makeUserCollaboratorRequest(GROUP, repository, ASSIGNEE))
        doHttpRequest(makeUserCollaboratorRequest(GROUP, repository, REVIEWER))
        doHttpRequest(makeUserCollaboratorRequest(GROUP, repository, APPROVER))
        testClient.commit(
            NewChangeSet("Commit ($issue)", testService.sshUrl(GROUP, repository), issue),
            "master"
        )
        check("Commit and branch for $issue have not been registered") {
            with(findByIssueKeys(setOf(issue))) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 0
            }
        }
        createPullRequest(
            testService.sshUrl(GROUP, repository),
            CreatePullRequest(issue, "master", "Webhook test PR", "Description $issue")
        )
        check("Pull request for $issue has not been registered") {
            with(findByIssueKeys(setOf(issue))) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 1
            }
        }
        val pullRequestIndex = findPullRequestsByIssueKeys(setOf(issue))[0].index
        check("Creation of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN
            }
        }
        doHttpRequest(setPullRequestAssigneeRequest(GROUP, repository, pullRequestIndex, ASSIGNEE))
        check("Assigning of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].assignees.size == 1 && this[0].assignees[0].name == ASSIGNEE
            }
        }
        doHttpRequest(addPullRequestReviewerRequest(GROUP, repository, pullRequestIndex, REVIEWER))
        doHttpRequest(addPullRequestReviewerRequest(GROUP, repository, pullRequestIndex, APPROVER))
        check("Addition of review requests for pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 2 && this[0].reviewers.none { it.approved } &&
                        this[0].reviewers.any { it.user.name == REVIEWER } && this[0].reviewers.any { it.user.name == APPROVER }
            }
        }
        doHttpRequest(deletePullRequestReviewerRequest(GROUP, repository, pullRequestIndex, REVIEWER))
        check("Deletion of review request for pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 1 && this[0].reviewers[0].user.name == APPROVER && !this[0].reviewers[0].approved
            }
        }
        doHttpRequest(approvePullRequestRequest(GROUP, repository, pullRequestIndex, APPROVER))
        check("Approval of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 1 && this[0].reviewers[0].user.name == APPROVER && this[0].reviewers[0].approved
            }
        }
        doHttpRequest(mergePullRequestRequest(GROUP, repository, pullRequestIndex))
        check("Merging of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKeys(setOf(issue))) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.MERGED
            }
        }
    }

    companion object {
        private const val ASSIGNEE = "test-assignee"
        private const val REVIEWER = "test-reviewer"
        private const val APPROVER = "test-approver"

        private fun check(failMessage: String, checkFunction: () -> Boolean) {
            var success = false
            for (i in 1..5) {
                Thread.sleep(1000L * i)
                success = checkFunction.invoke()
                if (success) break
            }
            if (!success) throw RuntimeException(failMessage)
        }

        //TODO: implement some of below functionality in Gitea client?
        //<editor-fold defaultstate="collapsed" desc="http requests data">

        private val httpClient = HttpClient.newHttpClient()

        private data class GiteaHttpRequest(
            val path: String,
            val method: String,
            val user: String,
            val password: String,
            val body: String,
            val failMessage: String
        )

        private fun doHttpRequest(giteaHttpRequest: GiteaHttpRequest) {
            with(
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI("http://$vcsHost/api/v1/${giteaHttpRequest.path}"))
                        .header(
                            "Authorization",
                            "Basic " + Base64.getEncoder()
                                .encodeToString("${giteaHttpRequest.user}:${giteaHttpRequest.password}".toByteArray())
                        )
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .method(giteaHttpRequest.method, HttpRequest.BodyPublishers.ofString(giteaHttpRequest.body))
                        .build(),
                    BodyHandlers.ofString()
                )
            ) {
                if (statusCode() / 100 != 2) {
                    throw RuntimeException("${giteaHttpRequest.failMessage}\n${body()}")
                }
            }
        }

        private fun userCreationRequest(user: String) = GiteaHttpRequest(
            "admin/users",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"email": "$user@domain.corp", "username": "$user", "password": "$user", "must_change_password": false}""",
            "Unable to create '$user' user"
        )

        private fun webhookCreationRequest(organization: String, repository: String) = GiteaHttpRequest(
            "repos/$organization/$repository/hooks",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{
    "type": "gitea",
    "branch_filter": "*",
    "config": {
        "content_type": "json",
        "url": "http://$vcsFacadeExternalHost/rest/api/1/indexer/gitea/webhook?vcsServiceId=test-gitea",
        "secret": "b59dd966-2445-4c84-b631-49502427477e"
    },
    "events": [
        "create",
        "delete",
        "push",
        "pull_request",
        "pull_request_approved",
        "pull_request_rejected"
    ],
    "authorization_header": "",
    "active": true
}""",
            "Unable to create webhook for '$organization:$repository' repository"
        )

        private fun makeUserCollaboratorRequest(organization: String, repository: String, user: String) =
            GiteaHttpRequest(
                "repos/$organization/$repository/collaborators/$user",
                "PUT",
                GITEA_USER,
                GITEA_PASSWORD,
                """{"permission": "write"}""",
                "Unable to make user '$user' collaborator of '$organization:$repository' repository"
            )

        private fun setPullRequestAssigneeRequest(organization: String, repository: String, index: Long, user: String) =
            GiteaHttpRequest(
                "repos/$organization/$repository/pulls/$index",
                "PATCH",
                GITEA_USER,
                GITEA_PASSWORD,
                """{"assignee": "$user"}""",
                "Unable to make user '$user' assignee in pull request $index in '$organization:$repository' repository"
            )

        private fun addPullRequestReviewerRequest(organization: String, repository: String, index: Long, user: String) =
            GiteaHttpRequest(
                "repos/$organization/$repository/pulls/$index/requested_reviewers",
                "POST",
                GITEA_USER,
                GITEA_PASSWORD,
                """{"reviewers": ["$user"]}""",
                "Unable to add review request for '$user' reviewer in pull request $index in '$organization:$repository' repository"
            )

        private fun deletePullRequestReviewerRequest(
            organization: String,
            repository: String,
            index: Long,
            user: String
        ) = GiteaHttpRequest(
            "repos/$organization/$repository/pulls/$index/requested_reviewers",
            "DELETE",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"reviewers": ["$user"]}""",
            "Unable to delete review request for '$user' reviewer in pull request $index in '$organization:$repository' repository"
        )

        private fun approvePullRequestRequest(organization: String, repository: String, index: Long, user: String) =
            GiteaHttpRequest(
                "repos/$organization/$repository/pulls/$index/reviews",
                "POST",
                user,
                user,
                """{"event": "APPROVED"}""",
                "Unable to approve pull request $index in '$organization:$repository' repository by '$user' user"
            )

        private fun mergePullRequestRequest(organization: String, repository: String, index: Long) = GiteaHttpRequest(
            "repos/$organization/$repository/pulls/$index/merge",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"Do": "merge"}""",
            "Unable to merge pull request $index in '$organization:$repository' repository"
        )
        //</editor-fold>
    }
}
