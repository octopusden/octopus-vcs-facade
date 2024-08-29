import java.util.Base64
import org.octopusden.octopus.task.DockerExecTask
import org.octopusden.octopus.task.OcCreateTask
import org.octopusden.octopus.task.OcDeleteTask
import org.octopusden.octopus.task.OcLogsTask
import org.octopusden.octopus.task.OcProcessTask

plugins {
    id("com.avast.gradle.docker-compose")
}

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "DEPLOYMENT_PREFIX" to "vcs-facade-ft-$version".replace("[^-a-z0-9]".toRegex(), "-"),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt()
)

fun String.getPort() = when (this) {
    "bitbucket" -> 7990
    "gitea" -> 3000
    "opensearch" -> 9200
    "vcs-facade" -> 8080
    else -> throw Exception("Unknown service '$this'")
}

fun String.getDockerHost() = "localhost:${getPort()}"

fun String.getDockerExternalHost() = "$this:${getPort()}"

fun String.getOkdPod() = "${commonOkdParameters["DEPLOYMENT_PREFIX"]}-$this"

fun String.getOkdHost() = "${getOkdPod()}-route-${"okdProject".getExt()}.${"okdClusterDomain".getExt()}:80"

fun String.getOkdExternalHost() = "${getOkdPod()}-service:${getPort()}"

val processGiteaTemplateTask = tasks.register<OcProcessTask>("processGiteaTemplate") {
    group = "okd"
    val file = "okd/gitea.yaml"
    template = rootProject.layout.projectDirectory.file(file)
    parameters.putAll(commonOkdParameters)
    parameters.put("GITEA_IMAGE_TAG", properties["gitea.image-tag"] as String)
    resourceList = layout.buildDirectory.file(file)
}

val createGiteaResourceTask = tasks.register<OcCreateTask>("createGiteaResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processGiteaTemplateTask.get().resourceList
    checkPodsReadiness = true
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("FT Gitea pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"gitea".getOkdPod()}")
            }
        }
    }
}

val logsGiteaResourceTask = tasks.register<OcLogsTask>("logsGiteaResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resource = "gitea".getOkdPod()
    resourceLog = layout.buildDirectory.file("okd/logs/${resource.get()}.log")
}

val deleteGiteaResourceTask = tasks.register<OcDeleteTask>("deleteGiteaResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processGiteaTemplateTask.get().resourceList
    mustRunAfter(logsGiteaResourceTask)
}

val processOpensearchTemplate = tasks.register<OcProcessTask>("processOpensearchTemplate") {
    group = "okd"
    val file = "okd/opensearch.yaml"
    template = rootProject.layout.projectDirectory.file(file)
    parameters.putAll(commonOkdParameters)
    parameters.put("OPENSEARCH_IMAGE_TAG", properties["opensearch.image-tag"] as String)
    resourceList = layout.buildDirectory.file(file)
}

val createOpensearchResourceTask = tasks.register<OcCreateTask>("createOpensearchResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processOpensearchTemplate.get().resourceList
    checkPodsReadiness = true
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("FT Opensearch pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"opensearch".getOkdPod()}")
            }
        }
    }
}

val logsOpensearchResourceTask = tasks.register<OcLogsTask>("logsOpensearchResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resource = "opensearch".getOkdPod()
    resourceLog = layout.buildDirectory.file("okd/logs/${resource.get()}.log")
}

val deleteOpensearchResourceTask = tasks.register<OcDeleteTask>("deleteOpensearchResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processOpensearchTemplate.get().resourceList
    mustRunAfter(logsOpensearchResourceTask)
}

val processBitbucketTemplate = tasks.register<OcProcessTask>("processBitbucketTemplate") {
    group = "okd"
    val file = "okd/bitbucket.yaml"
    template = rootProject.layout.projectDirectory.file(file)
    parameters.putAll(commonOkdParameters)
    parameters.put("BITBUCKET_LICENSE", Base64.getEncoder().encodeToString("bitbucketLicense".getExt().toByteArray()))
    parameters.put("BITBUCKET_IMAGE_TAG", properties["bitbucket.image-tag"] as String)
    parameters.put("POSTGRES_IMAGE_TAG", properties["postgres.image-tag"] as String)
    resourceList = layout.buildDirectory.file(file)
}

val createBitbucketResourceTask = tasks.register<OcCreateTask>("createBitbucketResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processBitbucketTemplate.get().resourceList
    checkPodsReadiness = true
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("FT Bitbucket pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket".getOkdPod()}")
            }
        }
    }
}

val logsBitbucketResourceTask = tasks.register<OcLogsTask>("logsBitbucketResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resource = "bitbucket".getOkdPod()
    resourceLog = layout.buildDirectory.file("okd/logs/${resource.get()}.log")
}

val deleteBitbucketResourceTask = tasks.register<OcDeleteTask>("deleteBitbucketResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processBitbucketTemplate.get().resourceList
    mustRunAfter(logsBitbucketResourceTask)
}

val processVcsFacadeTemplate = tasks.register<OcProcessTask>("processVcsFacadeTemplate") {
    group = "okd"
    val file = "okd/vcs-facade.yaml"
    template = rootProject.layout.projectDirectory.file(file)
    parameters.putAll(commonOkdParameters)
    parameters.put("VCS_FACADE_IMAGE_TAG", version as String)
    parameters.put("VCS_FACADE_VCS_TYPE", "testProfile".getExt())
    parameters.put("VCS_FACADE_VCS_HOST", "testProfile".getExt().getOkdExternalHost())
    parameters.put("VCS_FACADE_OPENSEARCH_HOST", "opensearch".getOkdExternalHost())
    resourceList = layout.buildDirectory.file(file)
}

val createVcsFacadeResourceTask = tasks.register<OcCreateTask>("createVcsFacadeResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processVcsFacadeTemplate.get().resourceList
    checkPodsReadiness = true
    dependsOn(":vcs-facade:dockerPushImage")
    mustRunAfter(createGiteaResourceTask, createOpensearchResourceTask, createBitbucketResourceTask)
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("FT Vcs-facade pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"vcs-facade".getOkdPod()}")
            }
        }
    }
}

val logsVcsFacadeResourceTask = tasks.register<OcLogsTask>("logsVcsFacadeResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resource = "vcs-facade".getOkdPod()
    resourceLog = layout.buildDirectory.file("okd/logs/${resource.get()}.log")
}

val deleteVcsFacadeResourceTask = tasks.register<OcDeleteTask>("deleteVcsFacadeResource") {
    group = "okd"
    namespace = "okdProject".getExt()
    resourceList = processVcsFacadeTemplate.get().resourceList
    mustRunAfter(logsVcsFacadeResourceTask)
}

configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"],
            "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"],
            "GITEA_IMAGE_TAG" to properties["gitea.image-tag"],
            "OPENSEARCH_IMAGE_TAG" to properties["opensearch.image-tag"],
            "VCS_FACADE_IMAGE_TAG" to version
        )
    )
}

tasks["composeUp"].dependsOn(":vcs-facade:dockerBuildImage")

sourceSets {
    create("ft") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val ftImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

ftImplementation.isCanBeResolved = true

configurations["ftRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val ft by tasks.creating(Test::class) {
    group = "verification"
    description = "Runs the integration tests"
    testClassesDirs = sourceSets["ft"].output.classesDirs
    classpath = sourceSets["ft"].runtimeClasspath
    systemProperties["test.profile"] = "testProfile".getExt()
    when ("testPlatform".getExt()) {
        "okd" -> {
            when ("testProfile".getExt()) {
                "gitea" -> {
                    dependsOn(createGiteaResourceTask, createOpensearchResourceTask)
                    finalizedBy(
                        logsGiteaResourceTask,
                        deleteGiteaResourceTask,
                        logsOpensearchResourceTask,
                        deleteOpensearchResourceTask
                    )
                }

                "bitbucket" -> {
                    dependsOn(createBitbucketResourceTask)
                    finalizedBy(logsBitbucketResourceTask, deleteBitbucketResourceTask)
                }
            }
            dependsOn(createVcsFacadeResourceTask)
            finalizedBy(logsVcsFacadeResourceTask, deleteVcsFacadeResourceTask)
            systemProperties["test.vcs-host"] = "testProfile".getExt().getOkdHost()
            systemProperties["test.vcs-external-host"] = "testProfile".getExt().getOkdExternalHost()
            systemProperties["test.vcs-facade-host"] = "vcs-facade".getOkdHost()
            systemProperties["test.vcs-facade-external-host"] = "vcs-facade".getOkdExternalHost()
        }

        "docker" -> {
            systemProperties["test.vcs-host"] = "testProfile".getExt().getDockerHost()
            systemProperties["test.vcs-external-host"] = "testProfile".getExt().getDockerExternalHost()
            systemProperties["test.vcs-facade-host"] = "vcs-facade".getDockerHost()
            systemProperties["test.vcs-facade-external-host"] = "vcs-facade".getDockerExternalHost()
            dockerCompose.isRequiredBy(this)
            if ("testProfile".getExt() == "gitea") {
                dependsOn(tasks.register<DockerExecTask>("createGiteaAdmin") {
                    group = "docker"
                    mustRunAfter("composeUp")
                    container = "vcs-facade-ft-gitea"
                    command.add("/script/add_admin.sh")
                })
            }

        }
    }
}

idea.module {
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftImplementation"])
}

dependencies {
    ftImplementation(project(":client"))
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
}
