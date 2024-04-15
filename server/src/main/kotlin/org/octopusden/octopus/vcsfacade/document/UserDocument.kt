package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

data class UserDocument(
    @Field(type = FieldType.Keyword) val name: String,
    @Field(type = FieldType.Keyword) val avatar: String?
)
