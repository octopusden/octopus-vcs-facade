package org.octopusden.octopus.vcsfacade.client.common.dto

data class CommitWithFiles(
    val commit: Commit,
    val totalFiles: Int,
    val files: List<FileChange>
) : Comparable<CommitWithFiles> {
    override fun compareTo(other: CommitWithFiles) = commit compareTo other.commit
}
