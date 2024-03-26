package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(Tag::class, name = "TAG"),
    JsonSubTypes.Type(Branch::class, name = "BRANCH")
)
abstract class Ref(
    val type: RefType,
    val name: String,
    val commitId: String,
    val link: String,
    val repository: Repository
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ref) return false
        if (type != other.type) return false
        if (name != other.name) return false
        if (commitId != other.commitId) return false
        if (link != other.link) return false
        if (repository != other.repository) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + commitId.hashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + repository.hashCode()
        return result
    }
}