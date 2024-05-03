package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.Objects

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(Branch::class, name = "BRANCH"),
    JsonSubTypes.Type(Tag::class, name = "TAG")
)
abstract class Ref(
    val type: RefType,
    val name: String,
    val hash: String,
    val link: String,
    val repository: Repository
) : Comparable<Ref> {
    override fun compareTo(other: Ref) = compareBy(Ref::repository, Ref::type, Ref::name).compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ref) return false
        if (type != other.type) return false
        if (name != other.name) return false
        if (hash != other.hash) return false
        if (link != other.link) return false
        if (repository != other.repository) return false
        return true
    }

    override fun hashCode() = Objects.hash(type, name, hash, link, repository)
}