package org.octopusden.octopus.vcsfacade.dto

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException

sealed class HashOrRefOrDate<out String, out Date> {
    class HashOrRefValue(val value: String) : HashOrRefOrDate<String, Nothing>() {
        override fun toString() = value
    }

    class DateValue(val value: Date) : HashOrRefOrDate<Nothing, Date>() {
        override fun toString() = value.toString()
    }

    companion object {
        fun create(hashOrRef: String?, date: Date?) = if (hashOrRef != null) {
            if (date != null) {
                throw ArgumentsNotCompatibleException("'hashOrRef' and 'date' can not be used together")
            }
            HashOrRefValue(hashOrRef)
        } else if (date != null) {
            DateValue(date)
        } else null
    }
}