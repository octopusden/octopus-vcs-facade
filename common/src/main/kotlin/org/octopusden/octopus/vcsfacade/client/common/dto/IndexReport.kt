package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class IndexReport(
    val repositories: List<IndexReportRepository>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IndexReportRepository(
        val sshUrl: String,
        val scanRequired: Boolean,
        val lastScanAt: Date?,
    )
}
