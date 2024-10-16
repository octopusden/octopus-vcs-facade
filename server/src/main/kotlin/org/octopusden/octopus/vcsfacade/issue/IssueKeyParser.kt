package org.octopusden.octopus.vcsfacade.issue

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException

object IssueKeyParser {
    private const val ISSUE_KEY_PATTERN = "[A-Z][_A-Z0-9]{1,9}-\\d+"

    private val issueKeyValidateRegex = ISSUE_KEY_PATTERN.toRegex()

    fun validateIssueKeys(issueKeys: Set<String>) = issueKeys.filter { !it.matches(issueKeyValidateRegex) }.let {
        if (it.isNotEmpty()) throw ArgumentsNotCompatibleException(
            it.sorted().joinToString(", ", "Invalid issue keys: ") { issueKey -> "'$issueKey'" }
        )
    }

    private val issueKeyFindRegex = "(?:^|[^_A-Z0-9])($ISSUE_KEY_PATTERN)".toRegex()

    fun findIssueKeys(message: String) = issueKeyFindRegex.findAll(message).map { it.groups[1]!!.value }

    fun getIssueKeyRegex(issueKey: String) = "(^|[^_A-Z0-9])$issueKey(\\D|$)".toRegex()
}