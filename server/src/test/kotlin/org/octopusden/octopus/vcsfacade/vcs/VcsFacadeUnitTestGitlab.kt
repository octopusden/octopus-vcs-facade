package org.octopusden.octopus.vcsfacade.vcs

import java.util.stream.Stream
import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import org.springframework.test.context.junit.jupiter.EnabledIf

private const val VCS_HOST = "localhost:8990"

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitlab'] == 'gitlab'}", loadContext = true)
class VcsFacadeUnitTestGitlab : BaseVcsFacadeUnitTest(
    GitlabTestClient("http://$VCS_HOST", GITLAB_USER, GITLAB_PASSWORD),
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
