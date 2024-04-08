package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.annotation.Id

abstract class Base(
    @Id
    val id: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Base
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        fun id(vararg fields: Any) = fields.joinToString("-") { it.toString() }.lowercase().replace('/', '-')
    }
}