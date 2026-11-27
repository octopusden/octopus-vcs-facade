package org.octopusden.octopus.vcsfacade.security

import org.octopusden.cloud.commons.security.BasePermissionEvaluator
import org.octopusden.cloud.commons.security.SecurityService
import org.springframework.stereotype.Component

/**
 * Referenced from `@PreAuthorize` as `@permissionEvaluator`.
 */
@Component("permissionEvaluator")
@ConditionalOnProvisioningEnabled
class ProvisioningPermissionEvaluator(
    securityService: SecurityService,
) : BasePermissionEvaluator(securityService)
