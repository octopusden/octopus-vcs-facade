package org.octopusden.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
open class Commit @JsonCreator constructor(
        val id: String,
        val message: String,
        // ToDo remove it together with fix deserialization at the Releng side
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM d, yyyy h:mm:s a", locale = "en_GB")
        val date: Date,
        val author: String,
        val parents: List<String>,
        val vcsUrl: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Commit) return false

        if (id != other.id) return false
        if (message != other.message) return false
        if (date != other.date) return false
        if (author != other.author) return false
        if (parents != other.parents) return false
        if (vcsUrl != other.vcsUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + parents.hashCode()
        result = 31 * result + vcsUrl.hashCode()
        return result
    }

    override fun toString(): String {
        return "Commit(id='$id', message='$message', date=$date, author='$author', parents=$parents, vcsUrl='$vcsUrl')"
    }
}
