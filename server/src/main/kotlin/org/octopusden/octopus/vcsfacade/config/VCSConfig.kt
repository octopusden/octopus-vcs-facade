package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VCSConfig(val giteaProperties: GiteaProperties?) {
    @ConfigurationProperties("vcs-facade.vcs.bitbucket")
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class BitbucketProperties(
        host: String, token: String?, username: String?, password: String?, healthCheck: HealthCheck
    ) : VCSProperties(
        host,
        if (token?.isNotBlank() == true) token else null,
        if (token?.isNotBlank() == true) null else username,
        if (token?.isNotBlank() == true) null else password,
        healthCheck
    )

    @ConfigurationProperties("vcs-facade.vcs.gitea")
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class GiteaProperties(
        host: String,
        token: String?,
        username: String?,
        password: String?,
        healthCheck: HealthCheck,
        val index: GiteaIndexProperties?
    ) : VCSProperties(
        host,
        if (token?.isNotBlank() == true) token else null,
        if (token?.isNotBlank() == true) null else username,
        if (token?.isNotBlank() == true) null else password,
        healthCheck
    )

    @ConfigurationProperties("vcs-facade.vcs.gitlab")
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.gitlab", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class GitLabProperties(
        host: String, token: String?, username: String?, password: String?, healthCheck: HealthCheck
    ) : VCSProperties(
        host,
        if (token?.isNotBlank() == true) token else null,
        if (token?.isNotBlank() == true) null else username,
        if (token?.isNotBlank() == true) null else password,
        healthCheck
    )

    data class GiteaIndexProperties(val webhookSecret: String?, val scan: GiteaIndexScanProperties?)

    data class GiteaIndexScanProperties(val cron: String?, val reindexCron: String?, val executor: ExecutorProperties?)

    abstract class VCSProperties(
        val host: String, val token: String?, val username: String?, val password: String?, val healthCheck: HealthCheck
    )

    data class HealthCheck(
        val repo: String, val rootCommit: String, val lastRelease: String, val expectedCommits: Set<String>
    )

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexScanCron() = giteaProperties?.index?.scan?.cron ?: "-"

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexScanReindexCron() = giteaProperties?.index?.scan?.reindexCron ?: "-"

    @Bean
    @ConditionalOnProperty(
        prefix = "vcs-facade", name = ["vcs.gitea.enabled", "opensearch.enabled"], havingValue = "true", matchIfMissing = true
    )
    fun giteaIndexScanExecutor() =
        (giteaProperties?.index?.scan?.executor ?: ExecutorProperties()).buildThreadPoolTaskExecutor()
}
