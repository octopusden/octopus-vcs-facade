package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

abstract class BaseRepositoryDocument(
    id: String,
    /*TODO: use nested objects instead of application-side foreign keys in case of:
    * - lack of performance
    * - necessity of complicated queries involving repository
    */
    @Field(type = FieldType.Keyword)
    val repositoryId: String
) : BaseDocument(id)