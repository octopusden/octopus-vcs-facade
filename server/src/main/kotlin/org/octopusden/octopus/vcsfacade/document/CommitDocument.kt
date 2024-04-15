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
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class CommitDocument(
    @Field(type = FieldType.Object) val repository: RepositoryDocument,
    @Field(type = FieldType.Keyword) val hash: String,
    @Field(type = FieldType.Text, analyzer = "classic") val message: String,
    @Field(type = FieldType.Date) val date: Date,
    @Field(type = FieldType.Object) val author: UserDocument,
    @Field(type = FieldType.Keyword) val parents: List<String>,
    @Field(type = FieldType.Keyword) val link: String,
) : BaseDocument(id(repository.id, hash)) {
    override fun toString() =
        "CommitDocument(id=$id, repository=$repository, hash=$hash, message=$message, date=$date, author=$author, parents=$parents, link=$link)"
}
