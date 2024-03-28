package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class VCSConfig(val giteaProperties: GiteaProperties?) {
    @ConfigurationProperties("vcs-facade.vcs.bitbucket")
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
        enabled: Boolean?,
        val index: GiteaIndexProperties?
    ) :
        VCSProperties(
            host,
            if (token?.isNotBlank() == true) token else null,
            if (token?.isNotBlank() == true) null else username,
            if (token?.isNotBlank() == true) null else password,
            healthCheck,
            enabled ?: true
        )

    @ConfigurationProperties("vcs-facade.vcs.gitlab")
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

    data class GiteaIndexProperties(val webhookSecret: String?, val scan: GiteaIndexScanProperties?)

    data class GiteaIndexScanProperties(val cron: String, val executor: ExecutorProperties)

    abstract class VCSProperties(
        val host: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck,
        val enabled: Boolean
    ) {
        class HealthCheck(
            val repo: String,
            val rootCommit: String,
            val lastRelease: String,
            val expectedCommits: Set<String>
        )
    }

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexScanCron() = giteaProperties?.index?.scan?.cron ?: "-"

    @Bean
    fun giteaIndexScanExecutor(): AsyncTaskExecutor? {
        return giteaProperties?.index?.scan?.executor?.let {
            ThreadPoolTaskExecutor().apply {
                corePoolSize = it.corePoolSize
                maxPoolSize = it.maxPoolSize
                queueCapacity = it.queueCapacity
            }
        }
    }
}
