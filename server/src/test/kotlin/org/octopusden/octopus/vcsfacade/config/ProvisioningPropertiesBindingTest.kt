package org.octopusden.octopus.vcsfacade.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.util.unit.DataSize

class ProvisioningPropertiesBindingTest {
    private val minimalService =
        arrayOf(
            "vcs-facade.vcs.services[0].id" to "gitea-1",
            "vcs-facade.vcs.services[0].type" to "GITEA",
            "vcs-facade.vcs.services[0].http-url" to "https://git.example.org",
            "vcs-facade.vcs.services[0].ssh-url" to "ssh://git@git.example.org",
            "vcs-facade.vcs.services[0].token" to "read-token",
        )

    private fun binder(vararg properties: Pair<String, String>) = Binder(MapConfigurationPropertySource(mapOf(*properties)))

    private fun bindProvisioning(vararg properties: Pair<String, String>) =
        binder(*properties).bindOrCreate("vcs-facade.provisioning", Bindable.of(ProvisioningProperties::class.java))

    private fun bindVcs(vararg properties: Pair<String, String>) =
        binder(*properties).bind("vcs-facade.vcs", Bindable.of(VcsProperties::class.java)).get()

    @Test
    fun `provisioning defaults apply when no keys are set`() {
        val properties = bindProvisioning()
        assertFalse(properties.enabled)
        assertEquals(DataSize.ofMegabytes(50), properties.maxContentSize)
        assertNull(properties.commitAuthor.name)
        assertNull(properties.commitAuthor.email)
    }

    @Test
    fun `provisioning binds every field with kebab-case keys`() {
        val properties =
            bindProvisioning(
                "vcs-facade.provisioning.enabled" to "true",
                "vcs-facade.provisioning.max-content-size" to "10MB",
                "vcs-facade.provisioning.commit-author.name" to "Provisioner",
                "vcs-facade.provisioning.commit-author.email" to "provisioner@example.org",
            )
        assertTrue(properties.enabled)
        assertEquals(DataSize.ofMegabytes(10), properties.maxContentSize)
        assertEquals("Provisioner", properties.commitAuthor.name)
        assertEquals("provisioner@example.org", properties.commitAuthor.email)
    }

    @Test
    fun `service without provisioning block still binds`() {
        val service = bindVcs(*minimalService).services.single()
        assertEquals("gitea-1", service.id)
        assertEquals(VcsServiceType.GITEA, service.type)
        assertEquals("read-token", service.token)
        assertNull(service.provisioning)
    }

    @Test
    fun `service provisioning binds token and allowed groups`() {
        val service =
            bindVcs(
                *minimalService,
                "vcs-facade.vcs.services[0].provisioning.token" to "write-token",
                "vcs-facade.vcs.services[0].provisioning.allowed-groups" to "team-a,team-b",
            ).services.single()
        val provisioning = checkNotNull(service.provisioning) { "provisioning block was not bound" }
        assertEquals("write-token", provisioning.token)
        assertNull(provisioning.username)
        assertNull(provisioning.password)
        assertEquals(setOf("team-a", "team-b"), provisioning.allowedGroups)
    }

    @Test
    fun `service provisioning binds username and password`() {
        val service =
            bindVcs(
                *minimalService,
                "vcs-facade.vcs.services[0].provisioning.username" to "writer",
                "vcs-facade.vcs.services[0].provisioning.password" to "secret",
            ).services.single()
        val provisioning = checkNotNull(service.provisioning) { "provisioning block was not bound" }
        assertNull(provisioning.token)
        assertEquals("writer", provisioning.username)
        assertEquals("secret", provisioning.password)
        assertTrue(provisioning.allowedGroups.isEmpty())
    }
}
