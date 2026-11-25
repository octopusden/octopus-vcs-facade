package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.GroupExistence
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.exception.ProvisioningNotImplementedException
import org.octopusden.octopus.vcsfacade.security.ConditionalOnProvisioningEnabled
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Repository provisioning reads; exists only when `vcs-facade.provisioning.enabled=true`.
 *
 * Every call needs the `CREATE_REPOSITORIES` permission.
 */
@RestController
@RequestMapping("rest/api/2")
@ConditionalOnProvisioningEnabled
class ProvisioningController {
    @GetMapping("groups", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getGroupExistence(
        @RequestParam("sshUrl") sshUrl: String,
    ): GroupExistence {
        log.info("Check the group of {}", sshUrl)
        throw ProvisioningNotImplementedException("The group check is not implemented yet")
    }

    @GetMapping("repository/state", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getRepositoryState(
        @RequestParam("sshUrl") sshUrl: String,
    ): RepositoryState {
        log.info("Get the state of repository {}", sshUrl)
        throw ProvisioningNotImplementedException("The repository state read is not implemented yet")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProvisioningController::class.java)
    }
}
