import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.util.concurrent.atomic.AtomicReference

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.octopusden.octopus.oc-template")
}

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
)

ocTemplate {
    workDir.set(layout.buildDirectory.dir("oc-template"))
    clusterDomain.set("okdClusterDomain".getExt())
    namespace.set("okdProject".getExt())
    prefix.set("deferred-result-it")
    service("valkey") {
        templateFile.set(layout.projectDirectory.file("../okd/valkey.yaml"))
        parameters.set(commonOkdParameters + mapOf("VALKEY_IMAGE_TAG" to properties["valkey.image-tag"] as String))
    }
}

dependencies {
    api("org.redisson:redisson:${properties["redisson.version"]}")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.slf4j:slf4j-api")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val valkeyLocalPort = 16379
val valkeyPortForwardProcess = AtomicReference<Process>()

val valkeyPortForward = tasks.register("valkeyPortForward") {
    group = "verification"
    description = "Starts oc port-forward to the Valkey service deployed in OKD"
    dependsOn("ocCreate")

    doLast {
        val namespace = ocTemplate.namespace.get()
        val service = "${ocTemplate.getPod("valkey")}-service"

        val process = ProcessBuilder(
            "oc",
            "port-forward",
            "-n",
            namespace,
            "svc/$service",
            "$valkeyLocalPort:6379",
        ).redirectErrorStream(true).start()

        Thread.sleep(2_000)

        if (!process.isAlive) {
            val output = process.inputStream
                .bufferedReader()
                .readText()
            throw GradleException(
                "Failed to start port-forward to $service " +
                    "in namespace $namespace.\n$output",
            )
        }

        valkeyPortForwardProcess.set(process)
    }
}

val valkeyPortForwardStop = tasks.register("valkeyPortForwardStop") {
    group = "verification"
    description = "Stops the oc port-forward to the Valkey service"
    doLast {
        valkeyPortForwardProcess.getAndSet(null)?.destroy()
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests against Valkey deployed in OKD"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
        )
    }
}

if ("testPlatform".getExt() == "okd") {
    integrationTest.configure {
        dependsOn(valkeyPortForward)
        finalizedBy(valkeyPortForwardStop)
        systemProperties["test.valkey-host"] = "localhost:$valkeyLocalPort"
    }
    ocTemplate.isRequiredBy(integrationTest)
    tasks.named("ocDelete") {
        mustRunAfter(valkeyPortForwardStop)
    }
} else {
    integrationTest.configure {
        doFirst {
            throw GradleException("Integration tests require -Ptest.platform=okd")
        }
    }
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
        )
    }
}
