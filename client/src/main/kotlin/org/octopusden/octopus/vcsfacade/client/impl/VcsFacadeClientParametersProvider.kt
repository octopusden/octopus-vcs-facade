package org.octopusden.octopus.vcsfacade.client.impl

interface VcsFacadeClientParametersProvider {
    fun getApiUrl(): String
    fun getTimeRetryInMillis(): Int
}
