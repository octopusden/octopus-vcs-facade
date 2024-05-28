package org.octopusden.octopus.vcsfacade

import java.util.Date
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.TestService.Companion.VCS_FACADE_API_URL
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

abstract class BaseVcsFacadeFunctionalTest(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {
    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest) =
        client.createPullRequest(sshUrl, createPullRequest)

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = client.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    companion object {
        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = VCS_FACADE_API_URL
            override fun getTimeRetryInMillis() = 180000
        })
    }
}
