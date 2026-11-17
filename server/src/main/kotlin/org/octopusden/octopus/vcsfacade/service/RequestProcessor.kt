package org.octopusden.octopus.vcsfacade.service

import java.lang.reflect.Type

interface RequestProcessor {
    fun <T : Any> process(
        requestId: String,
        type: Type,
        func: () -> T,
    ): T
}
