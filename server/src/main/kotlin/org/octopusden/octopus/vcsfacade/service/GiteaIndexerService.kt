package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.IndexReport

interface GiteaIndexerService {
    fun registerGiteaCreateRefEvent(host: String, giteaCreateRefEvent: GiteaCreateRefEvent)
    fun registerGiteaDeleteRefEvent(host: String, giteaDeleteRefEvent: GiteaDeleteRefEvent)
    fun registerGiteaPushEvent(host: String, giteaPushEvent: GiteaPushEvent)
    fun registerGiteaPullRequestEvent(host: String, giteaPullRequestEvent: GiteaPullRequestEvent)
    fun scheduleRepositoryScan(sshUrl: String)
    fun getIndexReport(): IndexReport
}