package org.octopusden.octopus.vcsfacade

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

class VcsFacadeBitbucketTest : BaseVcsFacadeFuncTest(
    BitbucketTestClient("localhost:7990", BITBUCKET_USER, BITBUCKET_PASSWORD),
    "ssh://git@bitbucket:7990/%s/%s.git"
) {
    override val exceptionsMessageInfo: Map<String, String> by lazy {
        mapOf(
            "absent-repo" to "Repository $PROJECT/absent does not exist.",
            "commitById" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$REPOSITORY'.",
            "commitsException_2" to "Params 'fromId' and 'fromDate' can not be used together",
            "commitsException_3" to "Cannot find commit '${MESSAGE_3.commitId(REPOSITORY)}' in commit graph for commit '${MESSAGE_1.commitId(REPOSITORY)}' in '$PROJECT:$REPOSITORY'",
            "pr_1" to "Project absent does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$PROJECT:$REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$PROJECT:$REPOSITORY'"
        )
    }
}
