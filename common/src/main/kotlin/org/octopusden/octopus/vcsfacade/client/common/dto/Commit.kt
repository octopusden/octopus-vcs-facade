package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.util.Date

open class Commit(
    val id: String,
    val message: String,
    //TODO: remove it together with fix deserialization at the Releng side
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM d, yyyy h:mm:s a", locale = "en_GB")
    val date: Date,
    val author: String,
    val parents: List<String>,
    val link: String,
    val vcsUrl: String,
    //TODO: backward compatibility, as possible - use User class to transfer author + authorAvatarUrl
    val authorAvatarUrl: String? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Commit) return false
        if (id != other.id) return false
        if (message != other.message) return false
        if (date != other.date) return false
        if (author != other.author) return false
        if (parents != other.parents) return false
        if (link != other.link) return false
        if (vcsUrl != other.vcsUrl) return false
        if (authorAvatarUrl != other.authorAvatarUrl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + parents.hashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + vcsUrl.hashCode()
        result = 31 * result + authorAvatarUrl.hashCode()
        return result
    }

    override fun toString(): String {
        return "Commit(id=$id, message=$message, date=$date, author=$author, parents=$parents, link=$link, vcsUrl=$vcsUrl, authorAvatarUrl=$authorAvatarUrl)"
    }
}
