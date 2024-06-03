package org.octopusden.octopus.vcsfacade.issue

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException

object IssueKeyParser {
    private const val ISSUE_KEY_PATTERN = "[A-Z][_A-Z0-9]{1,9}-\\d+"

    private val issueKeyValidateRegex = ISSUE_KEY_PATTERN.toRegex()

    fun validateIssueKey(issueKey: String) {
        if (!issueKey.matches(issueKeyValidateRegex)) {
            throw ArgumentsNotCompatibleException("Invalid issue key '$issueKey'")
        }
    }

    private val issueKeyFindRegex = "(?:^|[^_A-Z0-9-])($ISSUE_KEY_PATTERN)".toRegex()

    fun findIssueKeys(message: String) =
        issueKeyFindRegex.findAll(message).map { it.groups[1]!!.value }.toList().distinct()

    fun getIssueKeyRegex(issueKey: String) = "(^|[^_A-Z0-9-])$issueKey(\\D|$)".toRegex()
}