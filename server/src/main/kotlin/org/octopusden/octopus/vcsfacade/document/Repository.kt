package org.octopusden.octopus.vcsfacade.document

import java.util.Date
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-repositories-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class Repository(
    @Field(type = FieldType.Keyword) val type: VcsServiceType,
    @Field(type = FieldType.Keyword) val group: String,
    @Field(type = FieldType.Keyword) val name: String,
    @Field(type = FieldType.Date) var lastScanAt: Date? = null
) : Base(id(type, group, name)) {
    override fun toString() = "Repository(id=$id, type=$type, group=$group, name=$name, lastScanAt=$lastScanAt)"
}