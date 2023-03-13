package org.octopusden.vcsfacade.client

import feign.Param
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

class DateToISOExpander : Param.Expander {
    override fun expand(value: Any?): String {
        if (value == null) {
            return ""
        }
        return (value as? Date)
            ?.let { date ->
                return FORMATTER.format(date.toInstant())
            }
            ?: throw IllegalArgumentException("Value must be as java.util.Date but was '${value::class.qualifiedName}'")
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern(ISO_PATTERN)
            .withZone(ZoneId.systemDefault())
    }
}
