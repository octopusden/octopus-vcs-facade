package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("vcs-facade.provisioning")
data class ProvisioningProperties(
    @DefaultValue("false")
    val enabled: Boolean = false,
)
