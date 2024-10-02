package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.IndexReport

interface IndexerService {
    fun registerGiteaCreateRefEvent(host: String, createRefEvent: GiteaCreateRefEvent)
    fun registerGiteaDeleteRefEvent(host: String, deleteRefEvent: GiteaDeleteRefEvent)
    fun registerGiteaPushEvent(host: String, pushEvent: GiteaPushEvent)
    fun registerGiteaPullRequestEvent(host: String, pullRequestEvent: GiteaPullRequestEvent)
    fun scheduleRepositoryScan(sshUrl: String)
    fun getIndexReport(): IndexReport
}