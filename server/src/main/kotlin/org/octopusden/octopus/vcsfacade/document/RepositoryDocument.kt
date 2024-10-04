package org.octopusden.octopus.vcsfacade.document

import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType


class RepositoryDocument(
    @Field(type = FieldType.Keyword) val vcsServiceType: VcsServiceType,
    @Field(type = FieldType.Keyword) val vcsServiceId: String,
    @Field(type = FieldType.Keyword) val group: String,
    @Field(type = FieldType.Keyword) val name: String,
    @Field(type = FieldType.Keyword) val sshUrl: String,
    @Field(type = FieldType.Keyword) val link: String,
    @Field(type = FieldType.Keyword) val avatar: String?
) : BaseDocument(id(vcsServiceId, group, name)) {
    override fun toString() =
        "RepositoryDocument(id=$id, vcsServiceType=$vcsServiceType, vcsServiceId=$vcsServiceId, group=$group, name=$name, sshUrl=$sshUrl, link=$link, avatar=$avatar)"
}