package org.octopusden.octopus.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class DockerExecTask : DefaultTask() {
    @get:Input
    abstract val container: Property<String>

    @get:Input
    abstract val command: ListProperty<String>

    @TaskAction
    fun action() {
        project.exec {
            it.setCommandLine("docker", "exec", container.get(), * command.get().toTypedArray())
        }.assertNormalExitValue()
    }
}