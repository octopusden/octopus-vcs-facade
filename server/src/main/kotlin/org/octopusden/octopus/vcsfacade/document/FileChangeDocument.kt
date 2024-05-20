package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.client.common.dto.FileChangeType
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

data class FileChangeDocument(
    @Field(type = FieldType.Keyword) val type: FileChangeType,
    @Field(type = FieldType.Text, analyzer = "classic") val path: String,
    @Field(type = FieldType.Keyword) val link: String
)