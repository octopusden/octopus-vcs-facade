package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class RepositoryRange(val vcsPath: String, val fromCid: String?, val fromDate: Date?, val toCid: String)
