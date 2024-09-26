package org.octopusden.octopus.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class OcLogsTask : DefaultTask() {
    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val resource: Property<String>

    @get:OutputFile
    abstract val resourceLog: RegularFileProperty

    @TaskAction
    fun action() {
        project.exec {
            it.setCommandLine(
                "oc", "logs", "-n", namespace.get(), resource.get()
            )
            it.standardOutput = resourceLog.get().asFile.outputStream()
        }
    }
}