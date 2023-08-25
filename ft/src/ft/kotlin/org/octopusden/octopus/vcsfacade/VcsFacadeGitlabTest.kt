package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import java.util.stream.Stream

class VcsFacadeGitlabTest : BaseVcsFacadeFuncTest(
    GitlabTestClient("http://localhost:8990", GITLAB_USER, GITLAB_PASSWORD),
    "git@gitlab:8990:%s/%s.git"
) {

    //ToDo find implementation
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )
}
