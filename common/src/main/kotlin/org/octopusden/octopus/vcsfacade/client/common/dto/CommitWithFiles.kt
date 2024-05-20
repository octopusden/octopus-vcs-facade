package org.octopusden.octopus.vcsfacade.client.common.dto

data class CommitWithFiles(
    val commit: Commit,
    val files: List<FileChange>
) : VcsFacadeResponse, Comparable<CommitWithFiles> {
    override fun compareTo(other: CommitWithFiles) = commit compareTo other.commit
}
