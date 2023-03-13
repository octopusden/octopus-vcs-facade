package org.octopusden.vcsfacade.issue

object IssueKeyParser {
    private val maxProjectNameLength = 10
    private val projectKeyPattern = "(?:^|[^-a-zA-Z0-9])([a-zA-Z0-9]{1,$maxProjectNameLength}-\\d+)".toRegex()

    fun findIssueKeys(message: String) =
            projectKeyPattern.findAll(message).map { it.groups[1]!!.value }.toList().distinct()
}