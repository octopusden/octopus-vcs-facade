package org.octopusden.octopus.vcsfacade.client.common.dto

class Branch(name: String, commitId: String, link: String, repository: Repository) :
    Ref(RefType.BRANCH, name, commitId, link, repository) {
    override fun toString() = "Branch(name=$name, commitId=$commitId, link=$link, repository=$repository)"
}
