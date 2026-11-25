package org.octopusden.octopus.vcsfacade.exception

/**
 * Thrown by a provisioning endpoint that is routed and secured but not filled in yet.
 */
class ProvisioningNotImplementedException(
    message: String,
) : RuntimeException(message)
