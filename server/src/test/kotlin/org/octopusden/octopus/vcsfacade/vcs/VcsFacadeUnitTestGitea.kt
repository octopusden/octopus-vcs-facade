package org.octopusden.octopus.vcsfacade.vcs

import java.util.stream.Stream
import org.junit.jupiter.params.provider.Arguments
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.springframework.test.context.junit.jupiter.EnabledIf

private const val VCS_HOST = "localhost:3000"

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitea'] == 'gitea'}", loadContext = true)
class VcsFacadeUnitTestGitea : BaseVcsFacadeUnitTest(
    GiteaTestClient("http://$VCS_HOST", GITEA_USER, GITEA_PASSWORD),
    "ssh://git@$VCS_HOST/%s/%s.git"
) {
    //TODO: test using opensearch
    override fun issueCommits(): Stream<Arguments> = Stream.of(
        Arguments.of("ABSENT-1", emptyList<String>()),
    )

    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "The target couldn't be found.",
            "commitById" to DEFAULT_ID,
            "commitsException_1" to "object does not exist [id: $DEFAULT_ID, rel_path: ]",
            "commitsException_2" to "Params 'fromHashOrRef' and 'fromDate' can not be used together",
            "commitsException_3" to "Cannot find commit '${MESSAGE_3.commitId(REPOSITORY)}' in commit graph for commit '${MESSAGE_1.commitId(REPOSITORY)}' in '$PROJECT:$REPOSITORY'",
            "pr_1" to "GetUserByName",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
