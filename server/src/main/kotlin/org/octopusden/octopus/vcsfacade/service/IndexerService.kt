package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.IndexReport
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent

interface IndexerService {
    fun registerGiteaCreateRefEvent(vcsServiceId: String, createRefEvent: GiteaCreateRefEvent)
    fun registerGiteaDeleteRefEvent(vcsServiceId: String, deleteRefEvent: GiteaDeleteRefEvent)
    fun registerGiteaPushEvent(vcsServiceId: String, pushEvent: GiteaPushEvent)
    fun registerGiteaPullRequestEvent(vcsServiceId: String, pullRequestEvent: GiteaPullRequestEvent)
    fun scheduleRepositoryScan(sshUrl: String)
    fun getIndexReport(): IndexReport
}