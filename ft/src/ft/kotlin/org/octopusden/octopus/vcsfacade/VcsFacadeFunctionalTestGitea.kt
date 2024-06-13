package org.octopusden.octopus.vcsfacade

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
    TestService.Gitea(GITEA_HOST, GITEA_EXTERNAL_HOST, true),
    GiteaTestClient("http://$GITEA_HOST", GITEA_USER, GITEA_PASSWORD, GITEA_EXTERNAL_HOST)
) {
    @BeforeAll
    fun beforeAllVcsFacadeFunctionalTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
        doHttpRequest(webhookCreationRequest)
        doHttpRequest(userCreationRequest(ASSIGNEE))
        doHttpRequest(makeUserCollaboratorRequest(ASSIGNEE))
        doHttpRequest(userCreationRequest(REVIEWER))
        doHttpRequest(makeUserCollaboratorRequest(REVIEWER))
        doHttpRequest(userCreationRequest(APPROVER))
        doHttpRequest(makeUserCollaboratorRequest(APPROVER))
    }

    @Test
    fun webhooksTest() {
        testClient.commit(
            NewChangeSet("Commit ($ISSUE)", testService.sshUrl(GROUP, REPOSITORY_2), ISSUE),
            "master"
        )
        check("Commit and branch for $ISSUE have not been registered") {
            with(findByIssueKey(ISSUE)) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 0
            }
        }
        createPullRequest(
            testService.sshUrl(GROUP, REPOSITORY_2),
            CreatePullRequest(ISSUE, "master", "Webhook test PR", "Description $ISSUE")
        )
        check("Pull request for $ISSUE has not been registered") {
            with(findByIssueKey(ISSUE)) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 1
            }
        }
        val pullRequestIndex = findPullRequestsByIssueKey(ISSUE)[0].index
        check("Creation of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN
            }
        }
        doHttpRequest(setPullRequestAssigneeRequest(pullRequestIndex, ASSIGNEE))
        check("Assigning of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].assignees.size == 1 && this[0].assignees[0].name == ASSIGNEE
            }
        }
        doHttpRequest(addPullRequestReviewerRequest(pullRequestIndex, REVIEWER))
        doHttpRequest(addPullRequestReviewerRequest(pullRequestIndex, APPROVER))
        check("Addition of review requests for pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 2 && this[0].reviewers.none { it.approved } &&
                        this[0].reviewers.any { it.user.name == REVIEWER } && this[0].reviewers.any { it.user.name == APPROVER }
            }
        }
        doHttpRequest(deletePullRequestReviewerRequest(pullRequestIndex, REVIEWER))
        check("Deletion of review request for pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 1 && this[0].reviewers[0].user.name == APPROVER && !this[0].reviewers[0].approved
            }
        }
        doHttpRequest(approvePullRequestRequest(pullRequestIndex, APPROVER))
        check("Approval of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.OPEN &&
                        this[0].reviewers.size == 1 && this[0].reviewers[0].user.name == APPROVER && this[0].reviewers[0].approved
            }
        }
        doHttpRequest(mergePullRequestRequest(pullRequestIndex))
        check("Merging of pull request $pullRequestIndex has not been registered") {
            with(findPullRequestsByIssueKey(ISSUE)) {
                size == 1 && this[0].index == pullRequestIndex && this[0].status == PullRequestStatus.MERGED
            }
        }
    }

    companion object {
        private const val ISSUE = "ISSUE-10"
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
                        .uri(URI("http://$GITEA_HOST/api/v1/${giteaHttpRequest.path}"))
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

        private val webhookCreationRequest = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/hooks",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{
    "type": "gitea",
    "branch_filter": "*",
    "config": {
        "content_type": "json",
        "url": "http://vcs-facade:8080/rest/api/1/indexer/gitea/webhook",
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
            "Unable to create webhook for '$GROUP:$REPOSITORY_2' repository"
        )

        private fun userCreationRequest(user: String) = GiteaHttpRequest(
            "admin/users",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"email": "$user@domain.corp", "username": "$user", "password": "$user", "must_change_password": false}""",
            "Unable to create '$user' user"
        )

        private fun makeUserCollaboratorRequest(user: String) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/collaborators/$user",
            "PUT",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"permission": "write"}""",
            "Unable to make user '$user' collaborator of '$GROUP:$REPOSITORY_2' repository"
        )

        private fun setPullRequestAssigneeRequest(index: Long, user: String) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/pulls/$index",
            "PATCH",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"assignee": "$user"}""",
            "Unable to make user '$user' assignee in pull request $index in '$GROUP:$REPOSITORY_2' repository"
        )

        private fun addPullRequestReviewerRequest(index: Long, user: String) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/pulls/$index/requested_reviewers",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"reviewers": ["$user"]}""",
            "Unable to add review request for '$user' reviewer in pull request $index in '$GROUP:$REPOSITORY_2' repository"
        )

        private fun deletePullRequestReviewerRequest(index: Long, user: String) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/pulls/$index/requested_reviewers",
            "DELETE",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"reviewers": ["$user"]}""",
            "Unable to delete review request for '$user' reviewer in pull request $index in '$GROUP:$REPOSITORY_2' repository"
        )

        private fun approvePullRequestRequest(index: Long, user: String) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/pulls/$index/reviews",
            "POST",
            user,
            user,
            """{"event": "APPROVED"}""",
            "Unable to approve pull request $index in '$GROUP:$REPOSITORY_2' repository by '$user' user"
        )

        private fun mergePullRequestRequest(index: Long) = GiteaHttpRequest(
            "repos/$GROUP/$REPOSITORY_2/pulls/$index/merge",
            "POST",
            GITEA_USER,
            GITEA_PASSWORD,
            """{"Do": "merge"}""",
            "Unable to merge pull request $index in '$GROUP:$REPOSITORY_2' repository"
        )
        //</editor-fold>
    }
}
