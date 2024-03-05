package org.octopusden.octopus.vcsfacade.client.common.dto

class Tag(name: String, commitId: String, link: String, vcsUrl: String) :
    Ref(RefType.TAG, name, commitId, link, vcsUrl) {
    override fun toString() = "Tag(name=$name, commitId=$commitId, link=$link, vcsUrl=$vcsUrl)"
}
