package org.octopusden.octopus.vcsfacade.service

/**
 * The marker a provisioned repository's root commit carries as a trailer, `Provisioning-Marker: <marker>`.
 */
object ProvisioningMarker {
    /**
     * The trailer key the state read looks for.
     */
    const val TRAILER_KEY = "Provisioning-Marker"

    private val trailerLine = Regex("^([A-Za-z0-9][A-Za-z0-9-]*)[ \\t]*:(.*)$")

    /**
     * The value of the marker trailer, or `null` when there is none.
     *
     * A trailer is a `Key: value` line in the last paragraph of the message. The key matches without regard to
     * case, the value is trimmed, other trailers are ignored. With several marker trailers the last one counts.
     */
    fun parse(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val lines = message
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()
            .split('\n')
        val lastParagraph = lines.drop(lines.indexOfLast { it.isBlank() } + 1)
        return lastParagraph
            .mapNotNull { trailerLine.matchEntire(it)?.destructured }
            .lastOrNull { (key, _) -> key.equals(TRAILER_KEY, ignoreCase = true) }
            ?.component2()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
