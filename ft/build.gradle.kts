plugins {
    id("com.avast.gradle.docker-compose")
}

fun String.getExt() = project.ext[this] as? String

configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "VCS_FACADE_VERSION" to project.version,
            "BITBUCKET_VERSION" to project.properties["bitbucket.version"],
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "GITEA_VERSION" to project.properties["gitea.version"],
            "GITLAB_VERSION" to project.properties["gitlab.version"],
            "OPENSEARCH_VERSION" to project.properties["opensearch.version"],
            "POSTGRES_VERSION" to project.properties["postgres.version"]
        )
    )
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
    systemProperties["test.profile"] = "testProfile".getExt()
}

dockerCompose.isRequiredBy(ft)

tasks["composeUp"].doLast {
    if ("testProfile".getExt() == "gitea") {
        logger.info("Create test-admin in Gitea")
        val process = ProcessBuilder(
            "docker", "exec", "vcs-facade-ft-gitea",
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

tasks.named("composeUp") {
    dependsOn(":vcs-facade:dockerBuildImage")
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
