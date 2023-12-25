package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import java.util.stream.Stream

private const val vcsHost = "localhost:8990"

class RepositoryControllerGitlabTest :
    BaseRepositoryControllerTest(
        GitlabTestClient("http://$vcsHost", GITLAB_USER, GITLAB_PASSWORD),
        "ssh://git@$vcsHost:%s/%s.git"
    ) {

    //ToDo find implementation
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )

    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "Repository $PROJECT/absent does not exist.",
            "commitById" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_2" to "Params 'fromId' and 'fromDate' can not be used together",
            "commitsException_3" to "Can't find commit '${MESSAGE_3.commitId(REPOSITORY)}' in graph but it exists in the '${
                vcsRootFormat.format(
                    PROJECT,
                    REPOSITORY
                )
            }'",
            "pr_1" to "Project absent does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
