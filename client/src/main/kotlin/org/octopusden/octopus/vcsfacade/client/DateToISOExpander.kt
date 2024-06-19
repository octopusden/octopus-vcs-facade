package org.octopusden.octopus.vcsfacade.client

import feign.Param
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

class DateToISOExpander : Param.Expander {
    override fun expand(value: Any?) = if (value == null) "" else {
        (value as? Date)?.let { FORMATTER.format(it.toInstant()) }
            ?: throw IllegalArgumentException("Value must be as java.util.Date but was '${value::class.qualifiedName}'")
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault())
    }
}
