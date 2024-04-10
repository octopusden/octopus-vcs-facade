package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

abstract class BaseDocument(
    @Id
    @Field(type = FieldType.Keyword)
    val id: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BaseDocument
        return id == other.id
    }

    override fun hashCode() = id.hashCode()

    companion object {
        fun id(vararg fields: Any) = fields.joinToString("-") { it.toString() }.lowercase().replace('/', '-')
    }
}