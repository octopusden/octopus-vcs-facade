package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.vcsfacade.service.impl.toNamespaceAndProject
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals

class GitlabManagerTest : Spek({
    describe("a vcs url parser") {
        it("should correctly parse") {
            assertEquals(
                    "platform/module" to "module-v2",
                    "git@gitlab:platform/module/module-v2.git".toNamespaceAndProject()
            )
            assertEquals(
                    "one/two/three" to "project",
                    "git@gitlab:one/two/three/project.git".toNamespaceAndProject()
            )
            assertEquals(
                    "foo" to "bar",
                    "git@gitlab:foo/bar.git".toNamespaceAndProject()
            )
            assertEquals(
                    "tools/license-maven-plugin" to "license-maven-plugin",
                    "git@gitlab:/tools/license-maven-plugin/license-maven-plugin.git".toNamespaceAndProject()
            )
            assertEquals(
                "releng" to "vcs-facade-healthcheck",
                "ssh://git@bitbucket/releng/vcs-facade-healthcheck.git".replace(
                    "${"ssh://git@"}${"bitbucket"}[^/]*/".toRegex(),
                    ""
                )
                    .replace("\\.git$".toRegex(), "")
                    .let {
                        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
                    }
            )
            assertEquals(
                "releng" to "vcs-facade-healthcheck",
                "ssh://git@bitbucket.domain.corp/releng/vcs-facade-healthcheck.git".replace(
                    "${"ssh://git@"}${"bitbucket.domain.corp"}[^/]*/".toRegex(),
                    ""
                )
                    .replace("\\.git$".toRegex(), "")
                    .let {
                        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
                    }
            )
            assertEquals(
                "releng" to "vcs-facade-healthcheck",
                "ssh://git@bitbucket.domain.corp/releng/vcs-facade-healthcheck.git".replace(
                    "${"ssh://git@"}${"bitbucket"}[^/]*/".toRegex(),
                    ""
                )
                    .replace("\\.git$".toRegex(), "")
                    .let {
                        it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
                    }
            )
        }
    }
})

