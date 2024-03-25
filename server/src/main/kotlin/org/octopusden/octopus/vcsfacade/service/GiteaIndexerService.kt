package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaRepository

interface GiteaIndexerService {
    fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent)
    fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent)
    fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent)
    fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent)
    fun runRepositoryScan(giteaRepository: GiteaRepository)
}