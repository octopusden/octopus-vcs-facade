package org.octopusden.octopus.vcsfacade.client.impl

interface VcsFacadeClientParametersProvider {
    fun getApiUrl(): String

    fun getTimeRetryInMillis(): Int

    /**
     * The bearer token to send with every request, read again for each request; `null` sends none.
     * Only the repository provisioning endpoints need one.
     */
    fun getBearerToken(): String? = null
}
