import com.avast.gradle.dockercompose.ComposeExtension
import java.util.Base64
import org.octopusden.octopus.service.OcTemplateService

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
    "DEPLOYMENT_PREFIX" to "vcs-facade-ut-$version".replace("[^-a-z0-9]".toRegex(), "-"),
    "ACTIVE_DEADLINE_SECONDS" to "3600",
    "DOCKER_REGISTRY" to "dockerRegistry".getExt()
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

val gitea = gradle.sharedServices.registerIfAbsent("giteaUtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/gitea.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("GITEA_IMAGE_TAG", properties["gitea.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
}

val opensearch = gradle.sharedServices.registerIfAbsent("opensearchUtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/opensearch.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("OPENSEARCH_IMAGE_TAG", properties["opensearch.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
}

val bitbucket = gradle.sharedServices.registerIfAbsent("bitbucketUtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/bitbucket.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("BITBUCKET_LICENSE", Base64.getEncoder().encodeToString("bitbucketLicense".getExt().toByteArray()))
        templateParameters.put("BITBUCKET_IMAGE_TAG", properties["bitbucket.image-tag"] as String)
        templateParameters.put("POSTGRES_IMAGE_TAG", properties["postgres.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
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

tasks["composeUp"].doLast {
    if ("testProfile".getExt() == "gitea") {
        exec {
            setCommandLine("docker", "exec", "vcs-facade-ut-gitea", "/script/add_admin.sh")
        }.assertNormalExitValue()
    }
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
            systemProperties["test.opensearch-host"] = "opensearch".getOkdHost()
            systemProperties["test.vcs-host"] = "testProfile".getExt().getOkdHost()
            when ("testProfile".getExt()) {
                "gitea" -> {
                    usesService(gitea)
                    usesService(opensearch)
                    doFirst {
                        gitea.get().create()
                        opensearch.get().create()
                        gitea.get().waitPodsForReady()
                        opensearch.get().waitPodsForReady()
                        "okdWebConsoleUrl".getExt().let {
                            if (it.isNotBlank()) {
                                logger.quiet("UT gitea pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"gitea".getOkdPod()}")
                                logger.quiet("UT opensearch pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"opensearch".getOkdPod()}")
                            }
                        }
                    }
                    doLast {
                        gitea.get().logs("gitea".getOkdPod())
                        opensearch.get().logs("opensearch".getOkdPod())
                        gitea.get().delete()
                        opensearch.get().delete()
                    }
                }

                "bitbucket" -> {
                    usesService(bitbucket)
                    doFirst {
                        bitbucket.get().create()
                        bitbucket.get().waitPodsForReady()
                        "okdWebConsoleUrl".getExt().let {
                            if (it.isNotBlank()) {
                                logger.quiet("UT bitbucket-db pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket-db".getOkdPod()}")
                                logger.quiet("UT bitbucket pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket".getOkdPod()}")
                            }
                        }
                    }
                    doLast {
                        bitbucket.get().logs("bitbucket-db".getOkdPod())
                        bitbucket.get().logs("bitbucket".getOkdPod())
                        bitbucket.get().delete()
                    }
                }
            }
        }

        "docker" -> {
            systemProperties["test.opensearch-host"] = "opensearch".getDockerHost()
            systemProperties["test.vcs-host"] = "testProfile".getExt().getDockerHost()
            dockerCompose.isRequiredBy(this)
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
