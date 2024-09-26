package org.octopusden.octopus.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class OcDeleteTask : DefaultTask() {
    @get:Input
    abstract val namespace: Property<String>

    @get:InputFile
    abstract val resourceList: RegularFileProperty

    @TaskAction
    fun action() {
        project.exec {
            it.setCommandLine(
                "oc", "delete", "--ignore-not-found", "-n", namespace.get(),
                "-f", resourceList.get().asFile.absolutePath
            )
        }.assertNormalExitValue()
    }
}