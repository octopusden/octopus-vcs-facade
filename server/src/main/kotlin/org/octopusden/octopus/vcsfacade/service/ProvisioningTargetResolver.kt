package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
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
     * @throws InvalidRequestException when no configured VCS service supports [sshUrl], which then cannot be parsed either
     * @throws TargetNotAllowedException when the service has no provisioning credential or the group is not in its allow-list
     */
    fun resolve(sshUrl: String): ProvisioningTarget {
        // `getVcsServiceForSshUrl` signals an unsupported url with `IllegalStateException`; only that call is translated.
        val service = try {
            vcsManager.getVcsServiceForSshUrl(sshUrl)
        } catch (e: IllegalStateException) {
            throw InvalidRequestException("No configured VCS service supports '$sshUrl'").apply { initCause(e) }
        }
        val (group, repository) = service.parse(sshUrl)
        if (!service.isProvisioningAllowed(group)) {
            throw TargetNotAllowedException("Provisioning is not allowed for '$group' on VCS service '${service.id}'")
        }
        return ProvisioningTarget(service, group, repository)
    }
}
