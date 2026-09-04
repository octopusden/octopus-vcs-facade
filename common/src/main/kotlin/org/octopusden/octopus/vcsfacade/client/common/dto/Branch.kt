package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Branch(
    name: String,
    hash: String,
    link: String,
    repository: Repository,
) : Ref(RefType.BRANCH, name, hash, link, repository) {
    override fun toString() = "Branch(name=$name, hash=$hash, link=$link, repository=$repository)"
}
