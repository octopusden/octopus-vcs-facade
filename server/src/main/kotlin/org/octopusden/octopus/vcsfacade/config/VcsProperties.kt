package org.octopusden.octopus.vcsfacade.config

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider

sealed class VcsProperties(
    val instances: List<VcsInstanceProperties>
) {
    data class VcsInstanceProperties(
        val host: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck?
    ) {
        data class HealthCheck(
            val repo: String,
            val rootCommit: String,
            val lastRelease: String,
            val expectedCommits: Set<String>
        )

        val standardCredentialProvider by lazy {
            if (token != null) {
                StandardBearerTokenCredentialProvider(token)
            } else if (username != null && password != null) {
                StandardBasicCredCredentialProvider(username, password)
            } else {
                throw IllegalStateException("Auth token or username/password must be specified")
            }
        }

        val bitbucketCredentialProvider by lazy {
            if (token != null) {
                BitbucketBearerTokenCredentialProvider(token)
            } else if (username != null && password != null) {
                BitbucketBasicCredentialProvider(username, password)
            } else {
                throw IllegalStateException("Auth token or username/password must be specified")
            }
        }
    }
}