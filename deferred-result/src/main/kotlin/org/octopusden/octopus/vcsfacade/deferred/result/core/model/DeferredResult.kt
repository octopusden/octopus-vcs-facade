package org.octopusden.octopus.vcsfacade.deferred.result.core.model

data class DeferredResult<T>(
    val requestId: String,
    val status: DeferredResultStatus,
    val result: T? = null,
    val error: DeferredResultError? = null,
    val ownerId: String? = null,
)
