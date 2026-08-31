package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequest(
    val index: Long,
    val title: String,
    val description: String,
    val author: User,
    val source: String,
    val target: String,
    val assignees: List<User>,
    val reviewers: List<PullRequestReviewer>,
    val status: PullRequestStatus,
    val createdAt: Date,
    val updatedAt: Date,
    val link: String,
    val repository: Repository,
) : Comparable<PullRequest> {
    override fun compareTo(other: PullRequest) =
        compareBy(PullRequest::repository).thenByDescending(PullRequest::index).compare(this, other)
}
