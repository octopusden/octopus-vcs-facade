package org.octopusden.octopus.vcsfacade.client.common.dto

class Branch(name: String, commitId: String, link: String, vcsUrl: String) :
    Ref(RefType.BRANCH, name, commitId, link, vcsUrl) {
    override fun toString() = "Branch(name=$name, commitId=$commitId, link=$link, vcsUrl=$vcsUrl)"
}
