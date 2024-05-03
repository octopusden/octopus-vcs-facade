package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

private const val VCS_HOST = "bitbucket:7990"

@EnabledIfSystemProperty(named = "test.profile", matches = "bitbucket")
class VcsFacadeFunctionalTestBitbucket : BaseVcsFacadeFunctionalTest(
    BitbucketTestClient("http://localhost:7990", BITBUCKET_USER, BITBUCKET_PASSWORD, VCS_HOST),
    "ssh://git@$VCS_HOST/%s/%s.git"
) {
    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "Repository $PROJECT/absent does not exist.",
            "commitById" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_2" to "Params 'fromHashOrRef' and 'fromDate' can not be used together",
            "commitsException_3" to "Cannot find commit '${MESSAGE_3.commitId(REPOSITORY)}' in commit graph for commit '${MESSAGE_1.commitId(REPOSITORY)}' in '$PROJECT:$REPOSITORY'",
            "pr_1" to "Project absent does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
