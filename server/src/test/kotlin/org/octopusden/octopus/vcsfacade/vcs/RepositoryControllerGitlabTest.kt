package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import java.util.stream.Stream

private const val vcsHost = "localhost:8990"

class RepositoryControllerGitlabTest :
    BaseRepositoryControllerTest(
        GitlabTestClient("http://$vcsHost", GITLAB_USER, GITLAB_PASSWORD),
        "git@$vcsHost:%s/%s.git"
    ) {

    //ToDo find implementation
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )
}
