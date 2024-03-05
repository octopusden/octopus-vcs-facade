package org.octopusden.octopus.vcsfacade.issue

object IssueKeyParser {
    private val issueKeyPattern = "(?:^|[^_A-Z0-9-])([A-Z][_A-Z0-9]{1,9}-\\d+)".toRegex()

    fun findIssueKeys(message: String) =
        issueKeyPattern.findAll(message).map { it.groups[1]!!.value }.toList().distinct()

    fun getIssueKeyRegex(issueKey: String) = "(^|[^_A-Z0-9-])$issueKey(\\D|$)".toRegex()
}