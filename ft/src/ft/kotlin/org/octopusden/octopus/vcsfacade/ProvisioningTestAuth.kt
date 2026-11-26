package org.octopusden.octopus.vcsfacade

import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Bearer tokens for the provisioning endpoints, signed with a key pair generated once per JVM. The public half is registered as the key set of the
 * stub auth server through its admin API before the first token is issued, so no private key is stored in the repository.
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

    private val authServerHost = System.getProperty("test.auth-server-host")
        ?: throw Exception("System property 'test.auth-server-host' must be defined")

    private const val KEY_ID = "ft-key"

    /** Fixed id of the key set mapping: it is deleted before it is added, so registering again replaces it. */
    private const val JWKS_MAPPING_ID = "6f1c1f0e-5c3b-4a39-9a55-2f4f7d1b8e01"

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /**
     * vcs-facade fetches the key set on the first token it checks, so it has to be registered before any token is
     * used; every token comes from [sign], which touches this.
     */
    private val jwksRegistered by lazy { registerJwks() }

    /** The stub's userinfo gives this token a role that `octopus-security.roles` maps to `CREATE_REPOSITORIES`. */
    fun withPermission() = sign("ft-provisioner")

    /** The stub's userinfo gives this token a role without any permission. */
    fun withoutPermission() = sign("ft-guest")

    private fun sign(subject: String): String {
        jwksRegistered
        val now = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT","kid":"$KEY_ID"}"""
        val payload = """{"sub":"$subject","iss":"$issuer","iat":$now,"exp":${now + LIFETIME_SECONDS}}"""
        val signingInput = "${encode(header.toByteArray())}.${encode(payload.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(signingInput.toByteArray())
            sign()
        }
        return "$signingInput.${encode(signature)}"
    }

    private fun jwks(): String {
        val publicKey = keyPair.public as RSAPublicKey
        return """{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"$KEY_ID",""" +
            """"n":"${encode(unsigned(publicKey.modulus))}","e":"${encode(unsigned(publicKey.publicExponent))}"}]}"""
    }

    /** The big-endian magnitude without the sign byte that [BigInteger.toByteArray] adds. */
    private fun unsigned(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun registerJwks() {
        val mapping =
            """{"id":"$JWKS_MAPPING_ID","priority":1,""" +
                """"request":{"method":"GET","urlPath":"/realms/$REALM/protocol/openid-connect/certs"},""" +
                """"response":{"status":200,"headers":{"Content-Type":"application/json"},"body":${quote(jwks())}}}"""
        val client = HttpClient.newHttpClient()
        val mappings = "http://$authServerHost/__admin/mappings"
        // 404 is fine: nothing to replace on the first run.
        client.send(
            HttpRequest.newBuilder(URI("$mappings/$JWKS_MAPPING_ID")).DELETE().build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        val response = client.send(
            HttpRequest
                .newBuilder(URI(mappings))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapping))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() == 201) {
            "Cannot register the key set in the stub auth server: ${response.statusCode()} ${response.body()}"
        }
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
