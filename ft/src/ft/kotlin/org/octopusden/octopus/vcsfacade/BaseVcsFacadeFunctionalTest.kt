package org.octopusden.octopus.vcsfacade

import java.util.Date
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.impl.ClassicVcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.impl.VcsFacadeClientParametersProvider

abstract class BaseVcsFacadeFunctionalTest(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {
    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = client.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    companion object {
        private val client = ClassicVcsFacadeClient(object : VcsFacadeClientParametersProvider {
            override fun getApiUrl() = "http://localhost:8080"
            override fun getTimeRetryInMillis() = 180000
        })
    }
}
