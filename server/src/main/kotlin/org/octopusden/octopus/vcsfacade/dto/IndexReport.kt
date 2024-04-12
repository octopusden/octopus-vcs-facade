package org.octopusden.octopus.vcsfacade.dto

import java.util.Date

data class IndexReport(
    val repositories: List<IndexReportRepository>
) {
    data class IndexReportRepository(val sshUrl: String, val lastScanAt: Date?)
}
