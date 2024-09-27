package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("vcs-facade.vcs.bitbucket")
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class BitbucketProperties(instances: List<VcsInstanceProperties>) : VcsProperties(instances)
