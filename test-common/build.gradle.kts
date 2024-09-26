dependencies {
    implementation(project(":common"))
    implementation("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.junit.jupiter:junit-jupiter-params")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    api("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-test-client:${properties["external-systems-client.version"]}")
    api("org.octopusden.octopus.octopus-external-systems-clients:gitea-test-client:${properties["external-systems-client.version"]}")
}
