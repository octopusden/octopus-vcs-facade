package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.exception.ProvisioningNotImplementedException
import org.octopusden.octopus.vcsfacade.security.ConditionalOnProvisioningEnabled
import org.octopusden.octopus.vcsfacade.service.ProvisioningTargetResolver
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Repository provisioning; exists only when `vcs-facade.provisioning.enabled=true`.
 *
 * Every call first needs the `CREATE_REPOSITORIES` permission, then a target the allow-list accepts.
 */
@RestController
@RequestMapping("rest/api/2")
@ConditionalOnProvisioningEnabled
class ProvisioningController(
    private val targetResolver: ProvisioningTargetResolver,
) {
    @GetMapping("groups", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getGroupExistence(
        @RequestParam("sshUrl") sshUrl: String,
    ): Nothing {
        log.info("Check the group of {}", sshUrl)
        targetResolver.resolve(sshUrl)
        throw ProvisioningNotImplementedException("Group check is not implemented yet")
    }

    @GetMapping("repository/state", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getRepositoryState(
        @RequestParam("sshUrl") sshUrl: String,
    ): Nothing {
        log.info("Get the state of repository {}", sshUrl)
        targetResolver.resolve(sshUrl)
        throw ProvisioningNotImplementedException("Repository state is not implemented yet")
    }

    @PostMapping(
        "repository",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun createRepository(
        @RequestParam("sshUrl") sshUrl: String,
        @RequestParam("marker", required = false) marker: String?,
        @RequestParam("branch", required = false) branch: String?,
        @RequestParam("commitMessage", required = false) commitMessage: String?,
        @RequestPart("content", required = false) content: MultipartFile?,
    ): Nothing {
        log.info(
            "Create repository {} (marker length {}, branch {}, commit message given: {}, content bytes: {})",
            sshUrl,
            marker?.length,
            branch,
            commitMessage != null,
            content?.size,
        )
        targetResolver.resolve(sshUrl)
        throw ProvisioningNotImplementedException("Repository create is not implemented yet")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProvisioningController::class.java)
    }
}
