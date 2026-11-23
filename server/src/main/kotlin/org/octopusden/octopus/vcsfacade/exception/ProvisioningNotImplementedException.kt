package org.octopusden.octopus.vcsfacade.exception

import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

class ProvisioningNotImplementedException(
    message: String,
) : VcsFacadeException(message)
