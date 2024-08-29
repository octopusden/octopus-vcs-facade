import com.avast.gradle.dockercompose.ComposeExtension
import java.util.Base64
import org.octopusden.octopus.task.DockerExecTask
import org.octopusden.octopus.task.OcCreateTask
import org.octopusden.octopus.task.OcDeleteTask
import org.octopusden.octopus.task.OcLogsTask
import org.octopusden.octopus.task.OcProcessTask

plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.avast.gradle.docker-compose")
    id("com.bmuschko.docker-spring-boot-application")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bootJar") {
            artifact(tasks.getByName("bootJar"))
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Octopus module: ${project.name}")
                url.set("https://github.com/octopusden/octopus-vcs-facade.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/kzaporozhtsev/octopus-vcs-facade.git")
                    connection.set("scm:git://github.com/octopusden/octopus-vcs-facade.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
        }
    }
}

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["bootJar"])
}

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "DEPLOYMENT_PREFIX" to "vcs-facade-ut-$version",
    "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
    "PULL_SECRETS" to "okdPullSecrets".getExt()
)

fun String.getPort() = when (this) {
    "bitbucket" -> 7990
    "gitea" -> 3000
    "opensearch" -> 9200
    else -> throw Exception("Unknown service '$this'")
}

fun String.getDockerHost() = "localhost:${getPort()}"

fun String.getOkdPod() = "${commonOkdParameters["DEPLOYMENT_PREFIX"]}-$this"

fun String.getOkdHost() = "${getOkdPod()}-route-${"okdProject".getExt()}.${"okdClusterDomain".getExt()}:80"

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
                logger.quiet("UT Gitea pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"gitea".getOkdPod()}")
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

val processOpensearchTemplateTask = tasks.register<OcProcessTask>("processOpensearchTemplate") {
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
    resourceList = processOpensearchTemplateTask.get().resourceList
    checkPodsReadiness = true
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("UT Opensearch pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"opensearch".getOkdPod()}")
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
    resourceList = processOpensearchTemplateTask.get().resourceList
    mustRunAfter(logsOpensearchResourceTask)
}

val processBitbucketTemplateTask = tasks.register<OcProcessTask>("processBitbucketTemplate") {
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
    resourceList = processBitbucketTemplateTask.get().resourceList
    checkPodsReadiness = true
    doLast {
        "okdWebConsoleUrl".getExt().let {
            if (it.isNotBlank()) {
                logger.quiet("UT Bitbucket pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket".getOkdPod()}")
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
    resourceList = processBitbucketTemplateTask.get().resourceList
    mustRunAfter(logsBitbucketResourceTask)
}

configure<ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"],
            "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"],
            "GITEA_IMAGE_TAG" to properties["gitea.image-tag"],
            "OPENSEARCH_IMAGE_TAG" to properties["opensearch.image-tag"],
        )
    )
}

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:21-jdk")
        ports.set(listOf(8080, 8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/$name:$version"))
    }
}

tasks.withType<Test> {
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
            systemProperties["test.opensearch-host"] = "opensearch".getOkdHost()
            systemProperties["test.vcs-host"] = "testProfile".getExt().getOkdHost()
        }

        "docker" -> {
            systemProperties["test.opensearch-host"] = "opensearch".getDockerHost()
            systemProperties["test.vcs-host"] = "testProfile".getExt().getDockerHost()

            dockerCompose.isRequiredBy(this)
            if ("testProfile".getExt() == "gitea") {
                dependsOn(tasks.register<DockerExecTask>("createGiteaAdmin") {
                    group = "docker"
                    mustRunAfter("composeUp")
                    container = "vcs-facade-ut-gitea"
                    command.add("/script/add_admin.sh")
                })
            }
        }
    }
    systemProperties["test.vcs-facade-host"] = "localhost:8080"
    systemProperties["spring.profiles.active"] = "ut,${"testProfile".getExt()}"
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.retry:spring-retry")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.opensearch.client:spring-data-opensearch:${properties["spring-data-opensearch.version"]}")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${properties["springdoc-openapi.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${properties["external-systems-client.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:gitea-client:${properties["external-systems-client.version"]}")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
}

configurations.all {
    exclude("commons-logging", "commons-logging")
}
