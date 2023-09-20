package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Configuration

@Configuration
class VCSConfig {

    @ConfigurationProperties("vcs-facade.vcs.gitlab")
    @ConstructorBinding
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.gitlab",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    class GitLabProperties(
        host: String,
        token: String?,
        username: String?,
        password: String?,
        healthCheck: HealthCheck,
        enabled: Boolean?
    ) :
        VCSProperties(
            host,
            if (token?.isNotBlank() == true) token else null,
            if (token?.isNotBlank() == true) null else username,
            if (token?.isNotBlank() == true) null else password,
            healthCheck,
            enabled ?: true
        )

    @ConfigurationProperties("vcs-facade.vcs.bitbucket")
    @ConstructorBinding
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.bitbucket",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    class BitbucketProperties(
        host: String,
        token: String?,
        username: String?,
        password: String?,
        healthCheck: HealthCheck,
        enabled: Boolean?
    ) :
        VCSProperties(
            host,
            if (token?.isNotBlank() == true) token else null,
            if (token?.isNotBlank() == true) null else username,
            if (token?.isNotBlank() == true) null else password,
            healthCheck,
            enabled ?: true
        )

    @ConfigurationProperties("vcs-facade.vcs.gitea")
    @ConstructorBinding
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.gitea",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    class GiteaProperties(
        host: String,
        token: String?,
        username: String?,
        password: String?,
        healthCheck: HealthCheck,
        enabled: Boolean?
    ) :
        VCSProperties(
            host,
            if (token?.isNotBlank() == true) token else null,
            if (token?.isNotBlank() == true) null else username,
            if (token?.isNotBlank() == true) null else password,
            healthCheck,
            enabled ?: true
        )

    abstract class VCSProperties(
        val host: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck,
        val enabled: Boolean
    ) {

        @ConstructorBinding
        class HealthCheck(
            val repo: String,
            val rootCommit: String,
            val lastRelease: String,
            val expectedCommits: Set<String>
        )
    }
}
