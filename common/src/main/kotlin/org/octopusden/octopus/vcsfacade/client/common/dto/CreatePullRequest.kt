package org.octopusden.octopus.vcsfacade.client.common.dto

data class CreatePullRequest(
    val sourceBranch: String, val targetBranch: String, val title: String, val description: String
)
