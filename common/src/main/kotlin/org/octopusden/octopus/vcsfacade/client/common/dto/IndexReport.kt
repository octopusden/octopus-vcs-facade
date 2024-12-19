package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class IndexReport(
    val repositories: List<IndexReportRepository>
) {
    data class IndexReportRepository(val sshUrl: String, val scanRequired: Boolean, val lastScanAt: Date?)
}
