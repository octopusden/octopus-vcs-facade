package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException

/**
 * Checks of the create request's own fields, before the content or the VCS are touched.
 * The messages never repeat the marker or the branch as sent.
 */
object CreateRequestChecks {
    const val MAX_MARKER_LENGTH = 255

    /**
     * @return the marker and the branch, once known to be good
     * @throws InvalidRequestException when the marker is blank, over several lines, padded with whitespace or too long,
     *   or the branch is missing or blank
     */
    @Suppress("ThrowsCount") // one rejection per rule
    fun validate(
        marker: String?,
        branch: String?,
    ): Pair<String, String> {
        if (marker.isNullOrBlank()) throw InvalidRequestException("The marker must be given and not blank")
        if (marker.any { it == '\n' || it == '\r' }) throw InvalidRequestException("The marker must be a single line")
        if (marker != marker.trim()) throw InvalidRequestException("The marker must not start or end with whitespace")
        if (marker.length > MAX_MARKER_LENGTH) {
            throw InvalidRequestException("The marker must be at most $MAX_MARKER_LENGTH characters, it has ${marker.length}")
        }
        if (branch.isNullOrBlank()) throw InvalidRequestException("The branch must be given")
        return marker to branch
    }
}
