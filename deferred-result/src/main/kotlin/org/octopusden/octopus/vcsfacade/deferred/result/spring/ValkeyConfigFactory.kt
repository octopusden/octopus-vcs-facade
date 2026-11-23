package org.octopusden.octopus.vcsfacade.deferred.result.spring

import org.redisson.config.Config

object ValkeyConfigFactory {
    fun create(valkey: DeferredResultProperties.Valkey): Config {
        val config = Config()
            .setUsername(valkey.username)
            .setPassword(valkey.password)
        config
            .useSingleServer()
            .setAddress(address(valkey.host, valkey.port))
            .setDatabase(valkey.database)
            .setConnectionPoolSize(valkey.connectionPoolSize)
            .setConnectionMinimumIdleSize(valkey.connectionMinimumIdleSize)

        return config
    }

    private fun address(
        host: String,
        port: Int,
    ): String {
        val node = "$host:$port"
        return if (node.contains("://")) node else "valkey://$node"
    }
}
