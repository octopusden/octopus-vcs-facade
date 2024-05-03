package org.octopusden.octopus.vcsfacade.client.common.dto

class Tag(name: String, hash: String, link: String, repository: Repository) :
    Ref(RefType.TAG, name, hash, link, repository) {
    override fun toString() = "Tag(name=$name, hash=$hash, link=$link, repository=$repository)"
}
