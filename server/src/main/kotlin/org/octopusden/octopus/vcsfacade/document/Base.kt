package org.octopusden.octopus.vcsfacade.document

abstract class Base {
    open fun id(vararg fields: Any) = fields.joinToString("-") { it.toString() }.lowercase().replace('/', '-')
}