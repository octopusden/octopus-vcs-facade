package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Ref
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-refs-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class RefDocument(
    @Field(type = FieldType.Object) val repository: RepositoryDocument,
    @Field(type = FieldType.Keyword) val type: RefType,
    @Field(type = FieldType.Text, analyzer = "classic") val name: String,
    @Field(type = FieldType.Keyword) val hash: String,
    @Field(type = FieldType.Keyword) val link: String
) : BaseDocument(id(repository.id, type, name)) {
    val commitId = id(repository.id, hash)

    override fun toString() =
        "RefDocument(id=$id, repository=$repository, type=$type, name=$name, hash=$hash, link=$link)"

    fun toDto() = when (type) {
        RefType.BRANCH -> Branch(name, hash, link, repository.toDto())
        RefType.TAG -> Tag(name, hash, link, repository.toDto())
    }

    companion object {
        fun Ref.toDocument(repositoryDocument: RepositoryDocument) =
            RefDocument(repositoryDocument, type, name, commitId, link)
    }
}
