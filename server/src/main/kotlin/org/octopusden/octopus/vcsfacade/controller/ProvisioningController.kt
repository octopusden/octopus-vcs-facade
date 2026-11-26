package org.octopusden.octopus.vcsfacade.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.GroupExistence
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
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
@Tag(name = "Repository provisioning", description = "Reads and create for the component provisioning service")
// Declared here, not globally, so the scheme appears in the API description only when provisioning is enabled.
@SecurityScheme(name = ProvisioningController.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
// Error responses shared by every operation of this controller.
@ApiResponses(
    ApiResponse(
        responseCode = "400",
        description = "INVALID_REQUEST: the sshUrl is missing or no configured VCS service supports it",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    ),
    ApiResponse(
        responseCode = "401",
        description = "No token, or the token is rejected",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    ),
    ApiResponse(
        responseCode = "403",
        description = "The token lacks the CREATE_REPOSITORIES permission, or TARGET_NOT_ALLOWED: " +
            "the VCS has no provisioning credential or the group is not in its allow-list",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    ),
    ApiResponse(
        responseCode = "500",
        description = "The VCS could not be read (error code OTHER)",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    ),
)
class ProvisioningController(
    private val provisioningTargetResolver: ProvisioningTargetResolver,
) {
    @Operation(
        summary = "Check whether the project or organisation of a repository exists",
        description = "Takes a repository SSH URL and checks its Bitbucket project or Gitea organisation. " +
            "A missing group is a normal answer (`exists: false`); a failure to read it is an error status, never `exists: false`. " +
            "Requires the CREATE_REPOSITORIES permission.",
        security = [SecurityRequirement(name = BEARER_AUTH)],
    )
    @ApiResponse(responseCode = "200", description = "The group exists or is reported missing by the VCS")
    @GetMapping("groups", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getGroupExistence(
        @Parameter(description = "SSH URL of a repository in the group, as configured for a VCS service", required = true)
        @RequestParam("sshUrl") sshUrl: String,
    ): GroupExistence {
        val target = provisioningTargetResolver.resolve(sshUrl)
        log.info("Check group {} on VCS service {}", target.group, target.service.id)
        return GroupExistence(target.service.groupExists(target.group))
    }

    @Operation(
        summary = "Get the provisioning state of a repository",
        description = "Answers 200 with status MISSING when the VCS reports the repository or its group missing, " +
            "otherwise PRESENT with the description (marker), default branch, whether it has commits and the " +
            "Provisioning-Marker trailer of the root commit. A failure to read is an error status, never MISSING. " +
            "Requires the CREATE_REPOSITORIES permission.",
        security = [SecurityRequirement(name = BEARER_AUTH)],
    )
    @ApiResponse(responseCode = "200", description = "The state of the repository, MISSING or PRESENT")
    @GetMapping("repository/state", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    fun getRepositoryState(
        @Parameter(description = "SSH URL of the repository, as configured for a VCS service", required = true)
        @RequestParam("sshUrl") sshUrl: String,
    ): RepositoryState {
        val target = provisioningTargetResolver.resolve(sshUrl)
        log.info("Get the state of repository {}/{} on VCS service {}", target.group, target.repository, target.service.id)
        return target.service.getRepositoryState(target.group, target.repository)
    }

    @Operation(
        summary = "Create a repository, optionally with its first content",
        description = "Takes the repository SSH URL as a query parameter and the marker, default branch, optional commit message " +
            "and optional zip `content` as multipart parts. Requires the CREATE_REPOSITORIES permission. " +
            "Nothing is created yet: after the permission and allow-list checks the call answers 501.",
        security = [SecurityRequirement(name = BEARER_AUTH)],
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "413",
            description = "INVALID_CONTENT: the upload is larger than vcs-facade.provisioning.max-content-size",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "501",
            description = "The create is not implemented yet (error code OTHER)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("repository", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")
    @Suppress("UNUSED_PARAMETER", "UnusedParameter") // read by the request and content checks that follow
    fun createRepository(
        @Parameter(description = "SSH URL of the repository, as configured for a VCS service", required = true)
        @RequestParam("sshUrl") sshUrl: String,
        // Optional here so an upload over the limit, whose parts are then unreadable, answers 413 and not 400.
        @Parameter(description = "Stored as the repository description and as a trailer of the root commit")
        @RequestParam("marker", required = false) marker: String?,
        @Parameter(description = "The default branch of the repository")
        @RequestParam("branch", required = false) branch: String?,
        @Parameter(description = "The message of the root commit")
        @RequestParam("commitMessage", required = false) commitMessage: String?,
        @Parameter(description = "A zip with the initial files, sent as a file part")
        @RequestPart("content", required = false) content: MultipartFile?,
    ): Nothing {
        val target = provisioningTargetResolver.resolve(sshUrl)
        log.info("Create repository {}/{} on VCS service {}", target.group, target.repository, target.service.id)
        throw ProvisioningNotImplementedException("Repository create is not implemented yet")
    }

    companion object {
        const val BEARER_AUTH = "bearerAuth"

        private val log = LoggerFactory.getLogger(ProvisioningController::class.java)
    }
}
