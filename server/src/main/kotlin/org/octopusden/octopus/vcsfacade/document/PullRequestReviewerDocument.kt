package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.document.UserDocument.Companion.toDocument
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

data class PullRequestReviewerDocument(
    @Field(type = FieldType.Object) val user: UserDocument,
    @Field(type = FieldType.Boolean) val approved: Boolean
) {
    fun toDto() = PullRequestReviewer(user.toDto(), approved)

    companion object {
        fun PullRequestReviewer.toDocument() = PullRequestReviewerDocument(user.toDocument(), approved)
    }
}
