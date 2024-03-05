package org.octopusden.octopus.vcsfacade.document

import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

abstract class RepositoryLink(
    /*TODO: use nested objects instead of application-side foreign keys in case of:
    * - lack of performance
    * - necessity of complicated queries involving repository
    */
    @Field(type = FieldType.Text)
    val repositoryId: String
): Base() {
    override fun id(vararg fields: Any) = super.id(repositoryId, *fields)
}