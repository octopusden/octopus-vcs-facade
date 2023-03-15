package org.octopusden.octopus.vcsfacade.exception

import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException
import java.util.Date

class JobProcessingException(message: String, val requestId: String, val retryAfter: Date) : VcsFacadeException(message)
