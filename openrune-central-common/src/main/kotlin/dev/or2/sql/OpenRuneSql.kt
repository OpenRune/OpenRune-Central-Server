package dev.or2.sql

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Classpath `sql/…` text; [text] with pairs replaces markers like `__IN__`. */
public object OpenRuneSql {
    private const val PREFIX = "sql/"
    private val cache = ConcurrentHashMap<String, String>()

    public fun text(relativePath: String): String = cache.getOrPut(relativePath) { readRaw(relativePath) }

    public fun text(
        relativePath: String,
        vararg replacements: Pair<String, String>,
    ): String {
        var s = text(relativePath)
        for ((marker, value) in replacements) {
            s = s.replace(marker, value)
        }
        return s
    }

    private fun readRaw(relativePath: String): String {
        val normalized = relativePath.trim().removePrefix("/")
        val full = PREFIX + normalized
        val stream: InputStream =
            OpenRuneSql::class.java.classLoader.getResourceAsStream(full)
                ?: error("Missing SQL resource on classpath: $full")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    }
}
