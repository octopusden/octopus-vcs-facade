package org.octopusden.octopus.task

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class OcCreateTask : DefaultTask() {
    @get:Input
    abstract val namespace: Property<String>

    @get:InputFile
    abstract val resourceList: RegularFileProperty

    @get:Input
    abstract val checkPodsReadiness: Property<Boolean>

    @get:Input
    abstract val checkPodsReadinessAttempts: Property<Int>

    @get:Input
    abstract val checkPodsReadinessPeriod: Property<Long>

    init {
        checkPodsReadiness.convention(false)
        checkPodsReadinessAttempts.convention(20)
        checkPodsReadinessPeriod.convention(15000L)
    }

    @TaskAction
    fun action() {
        project.exec {
            it.setCommandLine(
                "oc", "create", "-n", namespace.get(),
                "-f", resourceList.get().asFile.absolutePath
            )
        }.assertNormalExitValue()
        //IMPORTANT: use "template.alpha.openshift.io/wait-for-ready" annotation for Deployments, Jobs, Builds etc.
        if (checkPodsReadiness.get()) { //there is no standard built-in way to wait Pods readiness
            var ready = false
            var counter = 0
            var output: OutputStream
            while (!ready && counter++ < checkPodsReadinessAttempts.get()) {
                Thread.sleep(checkPodsReadinessPeriod.get())
                output = ByteArrayOutputStream()
                project.exec {
                    it.setCommandLine(
                        "oc", "get", "-n", namespace.get(),
                        "-f", resourceList.get().asFile.absolutePath,
                        "-o", "jsonpath='{.items[*].status.containerStatuses[0].ready}'"
                    )
                    it.standardOutput = output
                }.assertNormalExitValue()
                if (!String(output.toByteArray()).contains("false")) {
                    ready = true
                } else {
                    logger.info("Pods are not ready yet...")
                }
            }
            if (!ready) {
                throw Exception("Readiness check attempts exceeded")
            }
            logger.quiet("Pods are ready")
        }
    }
}