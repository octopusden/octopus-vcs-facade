package org.octopusden.octopus.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class OcProcessTask : DefaultTask() {
    @get:InputFile
    abstract val template: RegularFileProperty

    @get:Input
    abstract val parameters: MapProperty<String, String>

    @get:OutputFile
    abstract val resourceList: RegularFileProperty

    @TaskAction
    fun action() {
        project.exec {
            it.setCommandLine(
                "oc", "process", "--local", "-o", "yaml",
                "-f", template.get().asFile.absolutePath,
                *parameters.get().flatMap { parameter ->
                    listOf("-p", "${parameter.key}=${parameter.value}")
                }.toTypedArray()
            )
            it.standardOutput = resourceList.get().asFile.outputStream()
        }.assertNormalExitValue()
    }
}