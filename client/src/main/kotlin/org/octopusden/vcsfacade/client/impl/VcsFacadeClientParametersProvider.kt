package org.octopusden.vcsfacade.client.impl

interface VcsFacadeClientParametersProvider {
    fun getApiUrl(): String
    fun getTimeRetryInMillis(): Int
}
