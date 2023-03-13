package org.octopusden.vcsfacade.client.common.dto

import java.util.Date

data class RetryResponse(val retryAfter: Date, val requestId: String)
