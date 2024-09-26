package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VcsConfig(val giteaProperties: GiteaProperties?) {
    @ConfigurationProperties("vcs-facade.vcs.bitbucket")
    @ConditionalOnProperty(
        prefix = "vcs-facade.vcs.bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class BitbucketProperties(
        host: String, token: String?, username: String?, password: String?, healthCheck: HealthCheck?
    ) : VcsProperties(
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
        healthCheck: HealthCheck?,
        val index: GiteaIndexProperties?
    ) : VcsProperties(
        host,
        if (token?.isNotBlank() == true) token else null,
        if (token?.isNotBlank() == true) null else username,
        if (token?.isNotBlank() == true) null else password,
        healthCheck
    )

    data class GiteaIndexProperties(val webhookSecret: String?, val scan: GiteaIndexScanProperties?)

    data class GiteaIndexScanProperties(val cron: String?, val delay: Long?, val executor: ExecutorProperties?)

    abstract class VcsProperties(
        val host: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck?
    )

    data class HealthCheck(
        val repo: String, val rootCommit: String, val lastRelease: String, val expectedCommits: Set<String>
    )

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexScheduleRepositoriesRescanCron() = giteaProperties?.index?.scan?.cron ?: "-"

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexSubmitScheduledRepositoriesScanFixedDelay() = giteaProperties?.index?.scan?.delay ?: 60000

    @Bean
    @ConditionalOnProperty(
        prefix = "vcs-facade",
        name = ["vcs.gitea.enabled", "opensearch.enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun giteaIndexScanExecutor() =
        (giteaProperties?.index?.scan?.executor ?: ExecutorProperties()).buildThreadPoolTaskExecutor()
}
