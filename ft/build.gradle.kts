plugins {
    id("com.avast.gradle.docker-compose") version "0.16.9"
}

val dockerRegistry = System.getenv().getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]) as? String
val octopusGithubDockerRegistry = System.getenv().getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]) as? String
val bitbucketLicense = System.getenv().getOrDefault("BITBUCKET_LICENSE", project.properties["bitbucket.license"]) as? String


configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(buildDir.resolve("docker_logs"))
    environment.putAll(
        mapOf(
            "APP_VERSION" to project.version,
            "DOCKER_REGISTRY" to dockerRegistry,
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to octopusGithubDockerRegistry,
            "BITBUCKET_LICENSE" to bitbucketLicense
        )
    )
}

tasks.getByName("composeUp").doFirst {
    if (dockerRegistry.isNullOrBlank() || octopusGithubDockerRegistry.isNullOrBlank() || bitbucketLicense.isNullOrBlank()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " -Poctopus.github.docker.registry=..." else "") +
                    (if (bitbucketLicense.isNullOrBlank()) " -Pbitbucket.license=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                    (if (bitbucketLicense.isNullOrBlank()) " BITBUCKET_LICENSE" else "")
        )
    }
}

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
}

dockerCompose.isRequiredBy(ft)

tasks.named("composeUp") {
    dependsOn(":vcs-facade:dockerBuildImage")
}

tasks.named("migrateMockData") {
    dependsOn("composeUp")
}

tasks.named("ft") {
    dependsOn("migrateMockData")
}

idea.module {
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftImplementation"])
}

dependencies {
    ftImplementation(project(":client"))
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation("org.junit.jupiter:junit-jupiter-engine:${project.properties["junit-jupiter.version"]}")
    ftImplementation("org.junit.jupiter:junit-jupiter-params:${project.properties["junit-jupiter.version"]}")
    ftImplementation("com.fasterxml.jackson.core:jackson-core")
    ftImplementation("com.fasterxml.jackson.core:jackson-databind")
    ftImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    ftImplementation("ch.qos.logback:logback-core:1.2.3")
    ftImplementation("ch.qos.logback:logback-classic:1.2.3")
    ftImplementation("org.slf4j:slf4j-api:1.7.30")
}
