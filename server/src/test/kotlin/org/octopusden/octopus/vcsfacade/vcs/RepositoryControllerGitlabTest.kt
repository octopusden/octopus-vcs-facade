package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import java.util.stream.Stream
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.test.context.ActiveProfiles

private const val VCS_HOST = "localhost:8990"

@EnabledIfSystemProperty(named = "test.profile", matches = "gitlab")
@ActiveProfiles("ut", "gitlab")
class RepositoryControllerGitlabTest : BaseRepositoryControllerTest(
    GitlabTestClient("http://$VCS_HOST", GITLAB_USER, GITLAB_PASSWORD),
    "ssh://git@$VCS_HOST:%s/%s.git",
    "http://$VCS_HOST/%s/%s/-/tree/%s?ref_type=tags",
    "http://$VCS_HOST/%s/%s/-/commit/%s"
) {
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )

    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "Repository '$PROJECT:absent' does not exist.",
            "commitById" to "Commit '$DEFAULT_ID' does not exist in repository '$PROJECT:$REPOSITORY'.",
            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$PROJECT:$REPOSITORY'.",
            "commitsException_2" to "Params 'fromId' and 'fromDate' can not be used together",
            "commitsException_3" to "Can't find commit '${MESSAGE_3.commitId(REPOSITORY)}' in graph but it exists in the '$PROJECT:$REPOSITORY'",
            "pr_1" to "Group 'absent' does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
