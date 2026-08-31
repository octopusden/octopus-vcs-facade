package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryRange(
    val sshUrl: String,
    val fromHashOrRef: String?,
    val fromDate: Date?,
    val toHashOrRef: String,
)
