package org.octopusden.octopus.vcsfacade

import java.util.stream.Stream
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient

private const val VCS_HOST = "gitlab:8990"

@EnabledIfSystemProperty(named = "test.profile", matches = "gitlab")
class VcsFacadeFunctionalTestGitlab : BaseVcsFacadeFunctionalTest(
    GitlabTestClient("http://localhost:8990", GITLAB_USER, GITLAB_PASSWORD, VCS_HOST),
    "ssh://git@$VCS_HOST:%s/%s.git"
) {
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )

    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "Repository '$PROJECT:absent' does not exist.",
            "commitById" to "Commit '$DEFAULT_ID' does not exist in repository '$PROJECT:$REPOSITORY'.",
            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$PROJECT:$REPOSITORY'.",
            "commitsException_2" to "Params 'fromHashOrRef' and 'fromDate' can not be used together",
            "commitsException_3" to "Can't find commit '${MESSAGE_3.commitId(REPOSITORY)}' in graph but it exists in the '$PROJECT:$REPOSITORY'",
            "pr_1" to "Group 'absent' does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
