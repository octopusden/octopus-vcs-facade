package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class Commit(
    val id: String,
    val message: String,
    val date: Date,
    val author: User,
    val parents: List<String>,
    val link: String,
    val vcsUrl: String
)
