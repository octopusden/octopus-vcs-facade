package org.octopusden.octopus.vcsfacade.client.common.exception

import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState

/**
 * @property state the repository state that was found, when the server sent it
 */
class RepositoryConflictException(
    message: String,
    val state: RepositoryState? = null,
) : VcsFacadeException(message)
