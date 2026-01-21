package dev.openrune.central.api

import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import dev.openrune.central.module
import dev.openrune.central.config.AppConfig
import dev.openrune.central.config.FlatGsonStorageConfig
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.crypto.Ed25519
import dev.openrune.central.world.WorldLocation
import dev.openrune.central.world.WorldType
import kotlinx.serialization.json.Json
import java.util.EnumSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivateAuthIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun logResult(testName: String, status: HttpStatusCode, body: String) {
        println("---- $testName ----")
        println("status=$status")
        println("body=$body")
        println("-------------------")
    }

    @Test
    fun `private test endpoint succeeds with real key`() = testApplication {
        environment { config = MapApplicationConfig() }

        val appConfig =
            AppConfig(
                rev = 235,
                name = "TestCentral",
                websiteUrl = "https://openrune.dev",
                storage = FlatGsonStorageConfig(
                    baseDir = "build/test-storage"
                ),
                worlds = listOf(
                    WorldConfig(
                        id = 1,
                        types = EnumSet.of(WorldType.MEMBERS),
                        address = "127.0.0.1",
                        activity = "Dev",
                        location = WorldLocation.UNITED_STATES,
                        authPublicKey = REAL_PUBLIC_KEY
                    )
                )
            )

        application {
            module(appConfig, generateJavaLocal = false)
        }

        suspend fun postSigned(privateKey: String, worldId: Int): HttpResponse {
            val path = "/api/private/test"
            val method = "POST"
            val body = """{"hello":"world"}"""
            val timestamp = System.currentTimeMillis().toString()
            val toSign = buildPrivateAuthPayload(timestamp, worldId.toString(), method, path, body)

            val sig = Ed25519.sign(privateKey, toSign)
            return client.post(path) {
                    contentType(ContentType.Application.Json)
                    header(PrivateAuthHeaders.WORLD_ID, worldId.toString())
                    header(PrivateAuthHeaders.TIMESTAMP_MS, timestamp)
                    header(PrivateAuthHeaders.SIGNATURE, sig)
                    setBody(body)
                }
        }

        // Correct key should succeed (200)
        val resp = postSigned(REAL_PRIVATE_KEY, 1)
        val body = resp.bodyAsText()
        logResult("success(real key)", resp.status, body)
        assertEquals(HttpStatusCode.OK, resp.status, "Expected 200 OK. body=$body")
        val ok = json.decodeFromString(PrivateAuthTestResponseDto.serializer(), body)
        assertTrue(ok.ok, "Expected ok=true. body=$body")
        assertEquals(1, ok.worldId, "Expected worldId=1. body=$body")
    }

    @Test
    fun `private test endpoint fails with wrong key`() = testApplication {
        environment { config = MapApplicationConfig() }

        val appConfig =
            AppConfig(
                rev = 235,
                name = "TestCentral",
                websiteUrl = "https://openrune.dev",
                storage = FlatGsonStorageConfig(
                    baseDir = "build/test-storage"
                ),
                worlds = listOf(
                    WorldConfig(
                        id = 1,
                        types = EnumSet.of(WorldType.MEMBERS),
                        address = "127.0.0.1",
                        activity = "Dev",
                        location = WorldLocation.UNITED_STATES,
                        authPublicKey = REAL_PUBLIC_KEY
                    )
                )
            )

        application {
            module(appConfig, generateJavaLocal = false)
        }

        val path = "/api/private/test"
        val method = "POST"
        val body = """{"hello":"world"}"""
        val timestamp = System.currentTimeMillis().toString()
        val toSign = buildPrivateAuthPayload(timestamp, "1", method, path, body)
        val sig = Ed25519.sign(FAKE_PRIVATE_KEY, toSign)
        val resp =
            client.post(path) {
                contentType(ContentType.Application.Json)
                header(PrivateAuthHeaders.WORLD_ID, "1")
                header(PrivateAuthHeaders.TIMESTAMP_MS, timestamp)
                header(PrivateAuthHeaders.SIGNATURE, sig)
                setBody(body)
            }

        val respBody = resp.bodyAsText()
        logResult("fail(wrong key)", resp.status, respBody)
        assertEquals(HttpStatusCode.Unauthorized, resp.status, "Expected 401. body=$respBody")
        val err = json.decodeFromString(ErrorResponseDto.serializer(), respBody)
        assertEquals("invalid signature", err.error, "Expected invalid signature. body=$respBody")
    }

    @Test
    fun `unknown world id returns 401 with knownWorldIds`() = testApplication {
        environment { config = MapApplicationConfig() }

        val appConfig =
            AppConfig(
                rev = 235,
                name = "TestCentral",
                websiteUrl = "https://openrune.dev",
                storage = FlatGsonStorageConfig(
                    baseDir = "build/test-storage"
                ),
                worlds = listOf(
                    WorldConfig(
                        id = 1,
                        types = EnumSet.of(WorldType.MEMBERS),
                        address = "127.0.0.1",
                        activity = "Dev",
                        location = WorldLocation.UNITED_STATES,
                        authPublicKey = REAL_PUBLIC_KEY
                    )
                )
            )

        application {
            module(appConfig, generateJavaLocal = false)
        }

        val path = "/api/private/test"
        val body = """{"hello":"world"}"""
        val timestamp = System.currentTimeMillis().toString()

        // Signature header still required, even though auth will fail before verification due to unknown world.
        val toSign = buildPrivateAuthPayload(timestamp, "999", "POST", path, body)

        val sig = Ed25519.sign(REAL_PRIVATE_KEY, toSign)
        val resp =
            client.post(path) {
                contentType(ContentType.Application.Json)
                header(PrivateAuthHeaders.WORLD_ID, "999")
                header(PrivateAuthHeaders.TIMESTAMP_MS, timestamp)
                header(PrivateAuthHeaders.SIGNATURE, sig)
                setBody(body)
            }

        val txt = resp.bodyAsText()
        logResult("fail(unknown world)", resp.status, txt)
        assertEquals(HttpStatusCode.Unauthorized, resp.status, "Expected 401. body=$txt")
        assertTrue(txt.contains("knownWorldIds"), "Expected knownWorldIds in body. body=$txt")
        assertTrue(txt.contains("1"), "Expected world id 1 in knownWorldIds. body=$txt")
    }

    // Test keys (never use in prod)
    // Real public key (central config) provided by you:
    private companion object {
        private const val REAL_PUBLIC_KEY = "MCowBQYDK2VwAyEAOyFt8oZRAmQyt82cZ_aG7uJRSrB6LjXAZN7J7vbM4yk"
        // Matching private key (world server) already used in your test client:
        private const val REAL_PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIJ1kzyZJb9R_ncUD1y0yo2kKqCg88wJig6kt4f4VAuK2"
        // Fake/wrong private key from earlier generated example:
        private const val FAKE_PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIGmEy82Snt-8EGfn1MmLcuF9sC-LfX3Yw6kDIMv32F0b"
    }
}

