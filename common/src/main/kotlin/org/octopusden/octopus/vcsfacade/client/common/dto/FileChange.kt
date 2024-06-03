package org.octopusden.octopus.vcsfacade.client.common.dto

data class FileChange(val type: FileChangeType, val path: String, val link: String) : Comparable<FileChange> {
    override fun compareTo(other: FileChange) = path compareTo other.path
}
