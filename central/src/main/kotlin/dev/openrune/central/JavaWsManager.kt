package dev.openrune.central

import dev.openrune.central.config.AppConfig
import dev.openrune.central.config.ConfigLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Ensures a root `java_local.ws` exists.
 *
 * - If missing, downloads a template using `config.rev`:
 *   `https://client.blurite.io/jav_local_{rev}.ws`
 * - If config changes (rev/name/websiteUrl), regenerates the file.
 * - Otherwise reuses the saved file.
 */
object JavaWsManager {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    private fun rootDir(): Path {
        val configPath = ConfigLoader.resolveConfigPath(Path.of("config.yml"))
        return configPath?.toAbsolutePath()?.parent ?: Path.of("").toAbsolutePath()
    }

    private val wsPath: Path get() = rootDir().resolve("java_local.ws")
    private val metaPath: Path get() = rootDir().resolve("java_local.meta")

    fun ensure(appConfig: AppConfig) {
        val desiredMeta = metaString(appConfig)
        val currentMeta = if (metaPath.exists()) metaPath.readText() else null

        val shouldRegen = !wsPath.exists() || currentMeta != desiredMeta
        if (!shouldRegen) return

        val template = downloadTemplate(appConfig.rev)
        val patched = patchTemplate(template, appConfig)

        wsPath.writeText(patched, StandardCharsets.UTF_8)
        metaPath.writeText(desiredMeta, StandardCharsets.UTF_8)
    }

    fun readText(): String = wsPath.readText(StandardCharsets.UTF_8)

    private fun downloadTemplate(rev: Int): String {
        val uri = URI("https://client.blurite.io/jav_local_${rev}.ws")
        val req = HttpRequest.newBuilder(uri).GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to download java_local template from $uri (HTTP ${resp.statusCode()})")
        }
        return resp.body()
    }

    private fun patchTemplate(template: String, config: AppConfig): String {
        val cachedir = config.name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
            .ifEmpty { "openrune" }

        val codebase = normalizeUrl(config.worlds.first().address)
        val websiteBase = config.websiteUrl.trimEnd('/')
        val worldListUrl = "http://127.0.0.1:8080/worldlist.ws"
        val cookieDomain = "." + websiteHost(config.websiteUrl)

        return template
            .replaceLineValue("title", config.name)
            .replaceLineValue("cachedir", cachedir)
            .replaceLineValue("codebase", codebase)
            .replaceLineValue("param=25", config.rev.toString())
            .replaceLineValue("param=17", worldListUrl)
            .replaceLineValue("param=13", cookieDomain)
    }

    private fun normalizeUrl(address: String): String {
        val trimmed = address.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
        return "http://$trimmed/"
    }

    private fun websiteHost(websiteUrl: String): String {
        val trimmed = websiteUrl.trim()
        val uri = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URI(trimmed)
        } else {
            URI("https://$trimmed")
        }
        return uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: "openrune.dev"
    }

    private fun metaString(config: AppConfig): String =
        "rev=${config.rev}\nname=${config.name}\nwebsiteUrl=${config.websiteUrl}\n"

    private fun String.replaceLineValue(key: String, newValue: String): String {
        val lines = lineSequence().toList()
        val replaced = lines.map { line ->
            when {
                line.startsWith("$key=") -> "$key=$newValue"
                line.startsWith("$key:") -> "$key: $newValue" // not expected, but safe
                else -> line
            }
        }
        return replaced.joinToString("\n")
    }
}