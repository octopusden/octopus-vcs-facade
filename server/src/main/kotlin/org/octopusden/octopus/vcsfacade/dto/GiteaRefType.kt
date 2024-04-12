package org.octopusden.octopus.vcsfacade.dto

import com.fasterxml.jackson.annotation.JsonValue
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType

enum class GiteaRefType(
    @get:JsonValue
    val jsonValue: String,
    val refType: RefType
) {
    BRANCH("branch", RefType.BRANCH), TAG("tag", RefType.TAG)
}