package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService

fun serviceProperties(
    type: VcsServiceType,
    provisioning: VcsProperties.Service.Provisioning?,
) = VcsProperties.Service(
    id = "test-${type.name.lowercase()}",
    type = type,
    httpUrl = "http://localhost:1/",
    sshUrl = "ssh://git@${type.name.lowercase()}.example.org",
    token = "read-token",
    username = null,
    password = null,
    healthCheck = null,
    provisioning = provisioning,
)

fun createService(
    type: VcsServiceType,
    provisioning: VcsProperties.Service.Provisioning?,
): VcsService =
    serviceProperties(type, provisioning).let {
        when (type) {
            VcsServiceType.BITBUCKET -> BitbucketService(it)
            VcsServiceType.GITEA -> GiteaService(it)
        }
    }
