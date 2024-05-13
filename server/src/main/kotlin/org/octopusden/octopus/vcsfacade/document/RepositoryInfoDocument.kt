package org.octopusden.octopus.vcsfacade.document

import java.util.Date
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
class RepositoryInfoDocument(
    @Field(type = FieldType.Object) val repository: RepositoryDocument,
    @Field(type = FieldType.Boolean) var scanRequired: Boolean = true,
    @Field(type = FieldType.Date) var lastScanAt: Date? = null
) : BaseDocument(id(repository.id)) {
    override fun toString() =
        "RepositoryInfoDocument(id=$id, repository=$repository, scanRequired=$scanRequired lastScanAt=$lastScanAt)"
}