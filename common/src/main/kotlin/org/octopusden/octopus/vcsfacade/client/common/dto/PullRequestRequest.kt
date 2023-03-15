package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestRequest @JsonCreator constructor(
    val sourceBranch: String, val targetBranch: String, val title: String, val description: String
)
