package org.octopusden.octopus.vcsfacade.document

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-repositories-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class RepositoryDocument(
    @Field(type = FieldType.Keyword) val type: VcsServiceType,
    @Field(type = FieldType.Keyword) val group: String,
    @Field(type = FieldType.Keyword) val name: String,
    @Field(type = FieldType.Keyword) val sshUrl: String,
    @Field(type = FieldType.Keyword) val link: String,
    @Field(type = FieldType.Keyword) val avatar: String?,
    @Field(type = FieldType.Date) var lastScanAt: Date? = null
) : BaseDocument(id(type, group, name)) {
    val fullName = "$group/$name"

    override fun toString() =
        "RepositoryDocument(id=$id, type=$type, group=$group, name=$name, sshUrl=$sshUrl, link=$link, avatar=$avatar, lastScanAt=$lastScanAt)"

    fun toDto() = Repository(sshUrl, link, avatar)
}