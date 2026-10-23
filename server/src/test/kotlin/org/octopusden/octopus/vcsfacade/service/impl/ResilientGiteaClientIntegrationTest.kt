package org.octopusden.octopus.vcsfacade.service.impl

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.CredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.getBranchesCommitGraph
import org.octopusden.octopus.infrastructure.gitea.client.getCommit
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import java.net.InetSocketAddress

class ResilientGiteaClientIntegrationTest {
    private lateinit var server: HttpServer

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/repos/org/repository/branches") { exchange ->
            exchange.respond(200, BRANCHES)
        }
        server.createContext("/api/v1/repos/org/repository/commits") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            if (query.contains("files=true")) {
                exchange.respond(200, COMMITS_TRUNCATED)
            } else {
                exchange.respond(200, COMMITS)
            }
        }
        server.createContext("/api/v1/repos/org/repository/git/commits/") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val sha = exchange.requestURI.path.substringAfterLast('/')
            when {
                sha == "c2" && query.contains("files=true") -> exchange.respond(200, COMMIT_C2)
                sha == "c2" -> exchange.respond(200, COMMIT_C2_WITHOUT_FILES)
                sha == "c1" && query.contains("files=true") -> exchange.respond(200, COMMIT_C1_TRUNCATED)
                sha == "c1" -> exchange.respond(200, COMMIT_C1_WITHOUT_FILES)
                sha == "empty" && query.contains("files=true") -> exchange.respond(200, "")
                sha == "empty" -> exchange.respond(200, COMMIT_EMPTY_WITHOUT_FILES)
                else -> exchange.respond(404, """{"message":"not found"}""")
            }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `real Feign client recovers truncated Gitea page`() {
        val client = ResilientGiteaClient(GiteaClassicClient(parametersProvider()))

        val commits = client.getBranchesCommitGraph(ORG, REPOSITORY, true).toList()

        assertEquals(listOf("c2", "c1"), commits.map { it.sha })
        assertEquals(listOf("src/Main.java"), commits[0].files.orEmpty().map { it.filename })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[1].files)
    }

    @Test
    fun `real Feign client recovers truncated date range`() {
        val client = ResilientGiteaClient(GiteaClassicClient(parametersProvider()))

        val commits = client.getCommits(ORG, REPOSITORY, "c2", null, true)

        assertEquals(listOf("c2", "c1"), commits.map { it.sha })
        assertEquals(listOf("src/Main.java"), commits[0].files.orEmpty().map { it.filename })
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commits[1].files)
    }

    @Test
    fun `real Feign client recovers truncated hash range`() {
        val client = ResilientGiteaClient(GiteaClassicClient(parametersProvider()))

        val commits = client.getCommits(ORG, REPOSITORY, "c2", "c1", true)

        val commit = commits.single()
        val files = commit.files.orEmpty().map { it.filename }
        assertEquals(listOf("c2"), commits.map { it.sha })
        assertEquals(listOf("src/Main.java"), files)
    }

    @Test
    fun `real Feign client recovers truncated single commit`() {
        val client = ResilientGiteaClient(GiteaClassicClient(parametersProvider()))

        val commit = client.getCommit(ORG, REPOSITORY, "c1", true)

        assertEquals("c1", commit.sha)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commit.files)
    }

    @Test
    fun `real Feign client recovers empty response body`() {
        val client = ResilientGiteaClient(GiteaClassicClient(parametersProvider()))

        val commit = client.getCommit(ORG, REPOSITORY, "empty", true)

        assertEquals("empty", commit.sha)
        assertEquals(emptyList<GiteaCommit.GiteaCommitAffectedFile>(), commit.files)
    }

    private fun parametersProvider() =
        object : ClientParametersProvider {
            override fun getApiUrl() = "http://127.0.0.1:${server.address.port}"

            override fun getAuth(): CredentialProvider = StandardBasicCredCredentialProvider("user", "password")
        }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        responseHeaders.add("Content-Type", "application/json")
        responseHeaders.add("X-HasMore", "false")
        val bytes = body.toByteArray()
        if (bytes.isEmpty()) {
            sendResponseHeaders(status, -1)
        } else {
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }
        close()
    }

    companion object {
        private const val ORG = "org"
        private const val REPOSITORY = "repository"

        private val BRANCHES =
            """
            [
              {
                "name": "main",
                "commit": {
                  "id": "c2"
                }
              }
            ]
            """.trimIndent()

        private const val COMMITS_TRUNCATED = """[{"sha":"c2","""

        private val COMMITS =
            """
            [
              {
                "sha": "c2",
                "created": "2024-01-02T00:00:00Z",
                "commit": {
                  "message": "message c2",
                  "author": {
                    "name": "author"
                  }
                },
                "parents": [
                  {
                    "sha": "c1"
                  }
                ]
              },
              {
                "sha": "c1",
                "created": "2024-01-01T00:00:00Z",
                "commit": {
                  "message": "message c1",
                  "author": {
                    "name": "author"
                  }
                },
                "parents": []
              }
            ]
            """.trimIndent()

        private val COMMIT_C2 =
            """
            {
              "sha": "c2",
              "created": "2024-01-02T00:00:00Z",
              "commit": {
                "message": "message c2",
                "author": {
                  "name": "author"
                }
              },
              "parents": [
                {
                  "sha": "c1"
                }
              ],
              "files": [
                {
                  "filename": "src/Main.java",
                  "status": "modified"
                }
              ]
            }
            """.trimIndent()

        private val COMMIT_C2_WITHOUT_FILES =
            """
            {
              "sha": "c2",
              "created": "2024-01-02T00:00:00Z",
              "commit": {
                "message": "message c2",
                "author": {
                  "name": "author"
                }
              },
              "parents": [
                {
                  "sha": "c1"
                }
              ]
            }
            """.trimIndent()

        private const val COMMIT_C1_TRUNCATED = """{"sha":"c1","""

        private val COMMIT_C1_WITHOUT_FILES =
            """
            {
              "sha": "c1",
              "created": "2024-01-01T00:00:00Z",
              "commit": {
                "message": "message c1",
                "author": {
                  "name": "author"
                }
              },
              "parents": []
            }
            """.trimIndent()

        private val COMMIT_EMPTY_WITHOUT_FILES =
            """
            {
              "sha": "empty",
              "created": "2024-01-03T00:00:00Z",
              "commit": {
                "message": "message empty",
                "author": {
                  "name": "author"
                }
              },
              "parents": []
            }
            """.trimIndent()
    }
}
