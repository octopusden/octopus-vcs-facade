package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.util.unit.DataSize

@ConfigurationProperties("vcs-facade.provisioning")
data class ProvisioningProperties(
    @DefaultValue("false")
    val enabled: Boolean = false,
    @DefaultValue("50MB")
    val maxContentSize: DataSize = DataSize.ofMegabytes(50),
    @DefaultValue
    val commitAuthor: CommitAuthor = CommitAuthor(),
) {
    data class CommitAuthor(
        val name: String? = null,
        val email: String? = null,
    )
}
