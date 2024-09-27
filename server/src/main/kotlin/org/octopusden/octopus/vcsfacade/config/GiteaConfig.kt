package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaConfig(val giteaProperties: GiteaProperties) {
    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexScheduleRepositoriesRescanCron() = giteaProperties.index?.scan?.cron ?: "-"

    @Bean //dedicated bean to simplify SpEL expression
    fun giteaIndexSubmitScheduledRepositoriesScanFixedDelay() = giteaProperties.index?.scan?.delay ?: 60000

    @Bean
    @ConditionalOnProperty(
        prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    fun giteaIndexScanExecutor() =
        (giteaProperties.index?.scan?.executor ?: ExecutorProperties()).buildThreadPoolTaskExecutor()
}
