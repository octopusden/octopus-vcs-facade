package org.octopusden.octopus.vcsfacade.document

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-pull-requests-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class PullRequestDocument(
    @Field(type = FieldType.Object) val repository: RepositoryDocument,
    @Field(type = FieldType.Long) val index: Long,
    @Field(type = FieldType.Text, analyzer = "classic") val title: String,
    @Field(type = FieldType.Text, analyzer = "classic") val description: String,
    @Field(type = FieldType.Object) val author: UserDocument,
    @Field(type = FieldType.Keyword) val source: String,
    @Field(type = FieldType.Keyword) val target: String,
    @Field(type = FieldType.Object) val assignees: List<UserDocument>,
    @Field(type = FieldType.Object) val reviewers: List<PullRequestReviewerDocument>,
    @Field(type = FieldType.Keyword) val status: PullRequestStatus,
    @Field(type = FieldType.Date) val createdAt: Date,
    @Field(type = FieldType.Date) val updatedAt: Date,
    @Field(type = FieldType.Keyword) val link: String
) : BaseDocument(id(repository.id, index)) {
    override fun toString() =
        "PullRequestDocument(id=$id, repository=$repository, index=$index, title=$title, description=$description, author=$author, source=$source, target=$target, assignees=$assignees, reviewers=$reviewers, status=$status, createdAt=$createdAt, updatedAt=$updatedAt, link=$link)"
}