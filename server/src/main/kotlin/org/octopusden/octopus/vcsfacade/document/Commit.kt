package org.octopusden.octopus.vcsfacade.document

import java.util.Date
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-commits-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class Commit(
    repositoryId: String,
    @Field(type = FieldType.Keyword) val hash: String,
    @Field(type = FieldType.Text, analyzer = "classic") val message: String,
    @Field(type = FieldType.Date) val date: Date
) : RepositoryLink(id(repositoryId, hash), repositoryId) {
    override fun toString() = "Commit(id=$id, repositoryId=$repositoryId, hash=$hash, message=$message, date=$date)"
}
