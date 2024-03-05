package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-refs-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "opensearch",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class Ref(
    repositoryId: String,
    @Field(type = FieldType.Keyword)
    val type: RefType,
    @Field(type = FieldType.Text, analyzer = "classic")
    val name: String,
    @Field(type = FieldType.Keyword)
    val hash: String
) : RepositoryLink(repositoryId) {
    @Id
    val id = id(type, name)
}
