package org.octopusden.octopus.vcsfacade

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Bearer tokens for the provisioning endpoints, signed with the test-only key whose public half the
 * stub auth server (`ft/docker/auth-server/mappings`) serves as its key set.
 *
 * The stub cannot see roles in a token (vcs-facade asks it for them through `userinfo`), so it tells the tokens
 * apart by the start of the payload: the first twelve bytes are `{"sub":"ft-p` or `{"sub":"ft-g`, and the mappings
 * match their base64url form. Keep the `sub` claim first and unspaced.
 */
object ProvisioningTestAuth {
    private const val REALM = "ft"
    private const val LIFETIME_SECONDS = 3600L

    private val authServerExternalHost = System.getProperty("test.auth-server-external-host")
        ?: throw Exception("System property 'test.auth-server-external-host' must be defined")

    /** The issuer vcs-facade expects: `auth-server.url` and the realm, as the server itself reaches the stub. */
    private val issuer = "http://$authServerExternalHost/realms/$REALM"

    private val privateKey by lazy {
        val pem = ProvisioningTestAuth::class.java.classLoader
            .getResourceAsStream("provisioning-test-key.pem")!!
            .bufferedReader()
            .useLines { lines -> lines.filterNot { it.startsWith("#") || it.startsWith("-----") }.joinToString("") }
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
    }

    /** The stub's userinfo gives this token a role that `octopus-security.roles` maps to `CREATE_REPOSITORIES`. */
    fun withPermission() = sign("ft-provisioner")

    /** The stub's userinfo gives this token a role without any permission. */
    fun withoutPermission() = sign("ft-guest")

    private fun sign(subject: String): String {
        val now = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT","kid":"ft-key"}"""
        val payload = """{"sub":"$subject","iss":"$issuer","iat":$now,"exp":${now + LIFETIME_SECONDS}}"""
        val signingInput = "${encode(header.toByteArray())}.${encode(payload.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray())
            sign()
        }
        return "$signingInput.${encode(signature)}"
    }

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
