package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.security.ConditionalOnProvisioningEnabled
import org.springframework.stereotype.Service

/**
 * The VCS service, group and repository a provisioning call is about.
 */
data class ProvisioningTarget(
    val service: VcsService,
    val group: String,
    val repository: String,
)

/**
 * Settles what a provisioning call may touch, before any VCS is called.
 */
@Service
@ConditionalOnProvisioningEnabled
class ProvisioningTargetResolver(
    private val vcsManager: VcsManager,
) {
    /**
     * @throws IllegalStateException when no configured VCS service supports [sshUrl], as every other endpoint does
     * @throws TargetNotAllowedException when the service has no provisioning credential or the group is not in its allow-list
     */
    fun resolve(sshUrl: String): ProvisioningTarget {
        val service = vcsManager.getVcsServiceForSshUrl(sshUrl)
        val (group, repository) = service.parse(sshUrl)
        if (!service.isProvisioningAllowed(group)) {
            throw TargetNotAllowedException("Provisioning is not allowed for '$group' on VCS service '${service.id}'")
        }
        return ProvisioningTarget(service, group, repository)
    }
}
