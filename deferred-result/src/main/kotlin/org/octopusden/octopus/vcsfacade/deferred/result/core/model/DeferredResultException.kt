package org.octopusden.octopus.vcsfacade.deferred.result.core.model

class DeferredResultException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
