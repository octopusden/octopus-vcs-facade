package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestReviewer(
    val user: User,
    val approved: Boolean,
)
