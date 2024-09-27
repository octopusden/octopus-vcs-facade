package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("vcs-facade.vcs.gitea")
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaProperties(
    instances: List<VcsInstanceProperties>,
    val index: GiteaIndexProperties?
) : VcsProperties(instances) {
    data class GiteaIndexProperties(
        val webhookSecret: String?,
        val scan: GiteaIndexScanProperties?
    )

    data class GiteaIndexScanProperties(
        val cron: String?,
        val delay: Long?,
        val executor: ExecutorProperties?
    )
}
