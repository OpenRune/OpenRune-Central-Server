package dev.or2.central.http

import dev.or2.central.config.CentralConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class JavConfigCache(
    private val config: CentralConfig,
) {
    private val log = LoggerFactory.getLogger(JavConfigCache::class.java)
    private val payload = AtomicReference<String?>(null)

    fun snapshot(): String? = payload.get()

    fun refresh() {
        try {
            payload.set(fetchAndMerge())
        } catch (e: Exception) {
            log.warn("jav_config refresh failed: {}", e.message)
        }
    }

    private fun fetchAndMerge(): String {
        val rev = config.javConfig.revision
        val url = String.format(config.javConfig.remoteUrlTemplate, rev)
        val remote = fetchRemote(url)
        return mergeProps(remote, config.javConfig.configProps)
    }

    private fun fetchRemote(url: String): String {
        val timeout = config.javConfig.httpTimeoutSeconds.toLong().coerceAtLeast(3)
        val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build()
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("jav_config remote HTTP ${response.statusCode()} for $url")
        }
        return response.body()
    }

    private fun mergeProps(remote: String, overrides: Map<String, String>): String {
        if (overrides.isEmpty()) return remote
        val lines = remote.lines().toMutableList()
        val indexByKey = linkedMapOf<String, Int>()
        lines.forEachIndexed { i, line ->
            val eq = line.indexOf('=')
            if (eq > 0) {
                indexByKey[line.substring(0, eq).trim()] = i
            }
        }
        for ((key, value) in overrides) {
            val formatted = "$key=$value"
            val existing = indexByKey[key]
            if (existing != null) {
                lines[existing] = formatted
            } else {
                lines.add(formatted)
            }
        }
        return lines.joinToString("\n")
    }
}
