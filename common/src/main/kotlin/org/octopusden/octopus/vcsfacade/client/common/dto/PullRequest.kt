package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class PullRequest(
    val id: Long,
    val title: String,
    val description: String,
    val source: String,
    val target: String,
    val assignee: List<User>,
    val reviewers: List<User>,
    val status: PullRequestStatus,
    val createdAt: Date,
    val updatedAt: Date,
    val link: String,
    val vcsUrl: String
)
