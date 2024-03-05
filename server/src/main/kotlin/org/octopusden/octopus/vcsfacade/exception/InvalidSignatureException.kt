package org.octopusden.octopus.vcsfacade.exception

import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

class InvalidSignatureException(message: String) : VcsFacadeException(message)