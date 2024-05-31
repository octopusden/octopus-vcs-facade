package org.octopusden.octopus.vcsfacade

import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infrastructure.common.test.dto.NewChangeSet
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest

@EnabledIfSystemProperty(named = "test.profile", matches = "gitea")
class VcsFacadeFunctionalTestGitea : BaseVcsFacadeFunctionalTest(
    TestService.Gitea(GITEA_HOST, GITEA_EXTERNAL_HOST, true),
    GiteaTestClient("http://$GITEA_HOST", GITEA_USER, GITEA_PASSWORD, GITEA_EXTERNAL_HOST)
) {
    @BeforeAll
    fun beforeAllVcsFacadeFunctionalTestGitea() {
        val url = URI("http://$GITEA_HOST/api/v1/repos/$GROUP/$REPOSITORY_2/hooks").toURL()
        with(url.openConnection() as HttpURLConnection) {
            setRequestMethod("POST")
            setRequestProperty(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString("$GITEA_USER:$GITEA_PASSWORD".toByteArray())
            )
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setDoOutput(true)
            outputStream.use {
                it.write(WEBHOOK_CREATION_REQUEST.toByteArray())
            }
            if (getResponseCode() / 100 != 2) {
                throw RuntimeException("Unable to create webhook for '$GROUP:$REPOSITORY_2'")
            }
        }
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
    }

    @Test
    fun webhooksTest() {
        var success = false
        testClient.commit(
            NewChangeSet("Commit (ISSUE-10)", testService.sshUrl(GROUP, REPOSITORY_2), "ISSUE-10"),
            "master"
        )
        for (i in 1..5) {
            Thread.sleep(1000L * i)
            success = with(findByIssueKey("ISSUE-10")) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 0
            }
            if (success) break
        }
        if (!success) throw RuntimeException("Commit and branch for ISSUE-10 have not been registered")
        createPullRequest(
            testService.sshUrl(GROUP, REPOSITORY_2),
            CreatePullRequest("ISSUE-10", "master", "Webhook test PR", "Description ISSUE-10")
        )
        for (i in 1..5) {
            Thread.sleep(1000L * i)
            success = with(findByIssueKey("ISSUE-10")) {
                branches.size == 1 && commits.size == 1 && pullRequests.size == 1
            }
            if (success) break
        }
        if (!success) throw RuntimeException("Pull request for ISSUE-10 has not been registered")
    }

    companion object {
        //<editor-fold defaultstate="collapsed" desc="webhook creation request">
        const val WEBHOOK_CREATION_REQUEST = """{
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
        "pull_request"
    ],
    "authorization_header": "",
    "active": true
}"""
        //</editor-fold>
    }
}
