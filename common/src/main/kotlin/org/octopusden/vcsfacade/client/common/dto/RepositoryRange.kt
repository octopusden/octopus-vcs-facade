package org.octopusden.vcsfacade.client.common.dto

import java.util.*

data class RepositoryRange(val vcsPath: String, val fromCid: String?, val fromDate: Date?, val toCid: String)
