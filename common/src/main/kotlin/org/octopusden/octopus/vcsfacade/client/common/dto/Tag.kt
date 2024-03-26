package org.octopusden.octopus.vcsfacade.client.common.dto

class Tag(name: String, commitId: String, link: String, repository: Repository) :
    Ref(RefType.TAG, name, commitId, link, repository) {
    override fun toString() = "Tag(name=$name, commitId=$commitId, link=$link, repository=$repository)"
}
