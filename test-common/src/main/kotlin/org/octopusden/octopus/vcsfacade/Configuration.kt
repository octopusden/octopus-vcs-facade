package org.octopusden.octopus.vcsfacade

import org.yaml.snakeyaml.Yaml

private const val PROPERTY_FILE = "test-properties.yaml"
const val TEST_PROFILE = "test.profile"
const val GITLAB = "gitlab"
const val GITEA = "gitea"
const val BITBUCKET = "bitbucket"

/**
 * Configuration object that reads the test-properties.yaml file and provides access to its properties.
 */
object Configuration {

    val model: ConfigModel by lazy {
        val yaml = Yaml()
        val inputStream = ConfigModel::class.java.getClassLoader().getResourceAsStream(PROPERTY_FILE)
        requireNotNull(inputStream) { "$PROPERTY_FILE not found" }
        overrideWithPropVariables(yaml.loadAs(inputStream, ConfigModel::class.java))
    }

    private fun overrideWithPropVariables(configModel: ConfigModel): ConfigModel {
        configModel.bitbucket.host = System.getProperty("bitbucketHost", configModel.bitbucket.host)
        configModel.bitbucket.externalHost = System.getProperty("bitbucketExternalHost", configModel.bitbucket.externalHost)
        configModel.bitbucket.url = System.getProperty("bitbucketUrl", configModel.bitbucket.url)
        configModel.gitea.host = System.getProperty("giteaHost", configModel.gitea.host)
        configModel.gitea.externalHost = System.getProperty("giteaExternalHost", configModel.gitea.externalHost)
        configModel.gitea.url = System.getProperty("giteaUrl", configModel.gitea.url)
        configModel.gitlab.host = System.getProperty("gitlabHost", configModel.gitlab.host)
        configModel.gitlab.externalHost = System.getProperty("gitlabExternalHost", configModel.gitlab.externalHost)
        configModel.gitlab.url = System.getProperty("gitlabUrl", configModel.gitlab.url)
        configModel.vcsFacadeUrl = System.getProperty("vcsFacadeUrl", configModel.vcsFacadeUrl)
        configModel.vcsFacadeInternalUrl = System.getProperty("vcsFacadeInternalUrl", configModel.vcsFacadeInternalUrl)
        return configModel
    }
}

/**
 * Configuration model representing the structure of the test-properties.yaml file.
 */
data class ConfigModel(
    var bitbucket: VcsModel = VcsModel(
        "admin",
        "admin",
        "localhost:7990",
        "bitbucket:7990",
        "http://localhost:7990"),
    var gitlab: VcsModel = VcsModel(
        "root",
        "VomkaEa6PD1OIgY7dQVbPUuO8wi9RMCaZw/i9yPXcI0=",
        "localhost:3000",
        "gitlab:8990",
        "http://localhost:8990"
    ),
    var gitea: VcsModel = VcsModel(
        "test-admin",
        "test-admin",
        "localhost:8990",
        "gitea:3000",
        "http://localhost:3000"),

    var vcsFacadeUrl: String = "http://localhost:8080",
    var vcsFacadeInternalUrl: String = "http://vcs-facade:8080",
    var project: String = "test-project",
    var repository: String = "test-repository",
    var repository2: String = "test-repository-2",
    var mainBranch: String = "master",
    var featureBranch: String = "feature/FEATURE-1",
    var messageInit: String = "initial commit",
    var message1: String = "TEST-1 First commit",
    var message2: String = "TEST-1 Second commit",
    var message3: String = "TEST-2 Third commit",
    var featureMessage: String = "FEATURE-1 First commit",
    var tag1: String = "commit-1-tag",
    var tag2: String = "commit-2-tag",
    var tag3: String = "commit-3-tag",
    var defaultId: String = "0123456789abcde",
)

/**
 * Configuration model representing the structure of the VCS properties.
 */
data class VcsModel(
    var user: String = "",
    var password: String = "",
    var host: String = "",
    var externalHost: String = "",
    var url: String = "",
)