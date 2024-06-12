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

fun String.getExt() = project.ext[this] as? String

configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "BITBUCKET_VERSION" to project.properties["bitbucket.version"],
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "GITEA_VERSION" to project.properties["gitea.version"],
            "GITLAB_VERSION" to project.properties["gitlab.version"],
            "OPENSEARCH_VERSION" to project.properties["opensearch.version"],
            "POSTGRES_VERSION" to project.properties["postgres.version"]
        )
    )
}

tasks["composeUp"].doLast {
    if ("testProfile".getExt() == "gitea") {
        logger.info("Create test-admin in Gitea")
        val process = ProcessBuilder(
            "docker", "exec", "vcs-facade-ut-gitea",
            "/tmp/add_admin.sh"
        ).start()
        process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        logger.info(output)
        val error = process.errorStream.bufferedReader().readText()
        if (error.isNotEmpty()) {
            throw GradleException(error)
        }
    }
}

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:21-jdk")
        ports.set(listOf(8080, 8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/${project.name}:${project.version}"))
    }
}

val helmNamespace: String? by project
val helmRelease = "helmRelease".getExt()
val clusterDomain: String? by project
val localDomain: String? by project
var bitbucketHost: String? = null
if ("platform".getExt() == "okd") {
    bitbucketHost = "$helmRelease-bitbucket-route-$helmNamespace.$clusterDomain"
}

tasks.withType<Test> {
    systemProperties["spring.profiles.active"] = "ut,${"testProfile".getExt()}"
    if ("platform".getExt() == "okd") {
        extensions.extraProperties["bitbucketHost"] = bitbucketHost
        systemProperties["bitbucketHost"] = bitbucketHost
        systemProperties["bitbucketUrl"] = "http://$bitbucketHost"
        systemProperties["vcs-facade.vcs.bitbucket.host"] = "http://$bitbucketHost"
        systemProperties["vcsFacadeUrl"] = "http://$helmRelease-vcs-facade-route-$helmNamespace.$clusterDomain"
    } else {
        dependsOn("composeUp")
    }
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
    implementation("org.opensearch.client:spring-data-opensearch:${project.properties["spring-data-opensearch.version"]}")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springdoc-openapi.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${project.properties["external-systems-client.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:gitea-client:${project.properties["external-systems-client.version"]}")
    implementation("org.gitlab4j:gitlab4j-api:${project.properties["gitlab4j-api.version"]}")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
}

configurations.all {
    exclude("commons-logging", "commons-logging")
}

if ("platform".getExt() == "okd") {
    println("Platform is OKD")

    tasks.named("test") {
        dependsOn(":deploy:deployHelm")
        finalizedBy(":deploy:uninstallHelm")
    }
} else {
    dockerCompose.isRequiredBy(tasks["test"])
}