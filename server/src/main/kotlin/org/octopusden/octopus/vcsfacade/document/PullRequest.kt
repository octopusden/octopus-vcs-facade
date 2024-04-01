package org.octopusden.octopus.vcsfacade.document

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "#{ 'vcs-facade-pull-requests-' + @opensearchIndexSuffix }")
@Setting(settingPath = "opensearch-index-settings.json")
@ConditionalOnProperty(
    prefix = "opensearch",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PullRequest(
    repositoryId: String,
    @Field(type = FieldType.Long)
    val index: Long,
    @Field(type = FieldType.Text, analyzer = "classic")
    val title: String,
    @Field(type = FieldType.Text, analyzer = "classic")
    val description: String,
    @Field(type = FieldType.Keyword)
    val status: PullRequestStatus,
    @Field(type = FieldType.Date)
    val updatedAt: Date
) : RepositoryLink(repositoryId) {
    @Id
    val id = id(index)

    override fun toString() = "PullRequest(id=$id, repositoryId=$repositoryId, index=$index, title=$title, description=$description, status=$status, updatedAt=$updatedAt)"
}