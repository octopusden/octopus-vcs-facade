val testProfile: String? = project.ext["testProfile"] as String?
val platform: String? = project.ext["platform"] as String?
val helmNamespace: String? = project.ext["helmNamespace"] as String?
val helmRelease: String? = project.ext["helmRelease"] as String?
val clusterDomain: String? = project.ext["clusterDomain"] as String?
val localDomain: String? = project.ext["localDomain"] as String?
var bitbucketHost: String? = project.ext["bitbucketHost"] as String?
val dockerRegistry: String? = project.ext["dockerRegistry"] as String?

tasks.register("uninstallHelm") {
    doLast {
        if (helmRelease == null) {
            throw GradleException("Helm release name is not set")
        }
        if (helmNamespace == null) {
            throw GradleException("Helm namespace is not set")
        }
        val result = exec {
            commandLine("helm", "uninstall", helmRelease, "--namespace", helmNamespace)
        }
        if (result.exitValue != 0) {
            throw GradleException("Helm uninstall failed with exit code ${result.exitValue}")
        }
    }
}

println("Profile: " + testProfile)
println("Platform: " + if (platform == "okd") "OKD" else "DOCKER-COMPOSE")

tasks.register<Exec>("deployHelm") {

    if (helmRelease == null) {
        throw GradleException("Helm release name is not set")
    }
    if (helmNamespace == null) {
        throw GradleException("Helm namespace is not set")
    }
    if (clusterDomain == null) {
        throw GradleException("Cluster domain is not set")
    }
    if (localDomain == null) {
        throw GradleException("Local domain is not set")
    }

    println("Release: $helmRelease")

    val bitbucketLicense: String by project

    commandLine("helm", "upgrade", "--wait"
        , "--install", helmRelease, "chart"
        , "--namespace", helmNamespace
        , "--set", "bitbucket.license=$bitbucketLicense"
        , "--set", "vcsFacade.image.version=${project.version}"
        , "--set", "bitbucket.host=${bitbucketHost}"
        , "--set", "clusterDomain=${clusterDomain}"
        , "--set", "localDomain=${localDomain}"
        , "--set", "dockerRegistry=$dockerRegistry"
        , "--set", "platform=openshift"
    )
    doLast {
        val execResult = executionResult.get()
        if (execResult.exitValue != 0) {
            val errorOutput = standardOutput.toString()
            throw GradleException("Helm deploy failed with exit code ${execResult.exitValue} and the following error:\\n$errorOutput")
        }
    }
}