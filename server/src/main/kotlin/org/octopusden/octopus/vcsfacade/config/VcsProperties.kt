package org.octopusden.octopus.vcsfacade.config

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("vcs-facade.vcs")
data class VcsProperties(
    val services: List<Service>
) {
    data class Service(
        val id: String,
        val type: VcsServiceType,
        val httpUrl: String,
        val sshUrl: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck?
    ) {
        fun getCredentialProvider() = if (token != null) {
            if (type == VcsServiceType.BITBUCKET) {
                BitbucketBearerTokenCredentialProvider(token)
            } else {
                StandardBearerTokenCredentialProvider(token)
            }
        } else if (username != null && password != null) {
            if (type == VcsServiceType.BITBUCKET) {
                BitbucketBasicCredentialProvider(username, password)
            } else {
                StandardBasicCredCredentialProvider(username, password)
            }
        } else {
            throw IllegalStateException("Auth token or username/password must be specified")
        }

        data class HealthCheck(
            val group: String,
            val repository: String,
            val fromCommit: String,
            val toCommit: String,
            val expectedCommits: Set<String>
        )
    }
}