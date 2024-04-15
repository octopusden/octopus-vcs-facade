package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

data class PullRequestReviewerDocument(
    @Field(type = FieldType.Object) val user: UserDocument,
    @Field(type = FieldType.Boolean) val approved: Boolean
)
