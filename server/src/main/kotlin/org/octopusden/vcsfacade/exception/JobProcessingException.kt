package org.octopusden.vcsfacade.exception

import org.octopusden.vcsfacade.client.common.exception.VcsFacadeException
import java.util.Date

class JobProcessingException(message: String, val requestId: String, val retryAfter: Date) : VcsFacadeException(message)
