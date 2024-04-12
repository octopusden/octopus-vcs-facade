package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

data class UserDocument(
    @Field(type = FieldType.Keyword) val name: String,
    @Field(type = FieldType.Keyword) val avatar: String?
) {
    fun toDto() = User(name, avatar)

    companion object {
        fun User.toDocument() = UserDocument(name, avatar)
    }
}
