package dev.or2.central

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.JarURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import javax.sql.DataSource

private const val SCHEMA_RESOURCE_DIR = "db/schema"

object CentralSchemaBootstrap {
    fun apply(dataSource: DataSource) {
        val cl = Thread.currentThread().contextClassLoader
        val schemaFragments = discoverSchemaSqlPaths(cl)
        require(schemaFragments.isNotEmpty()) {
            "No *.sql files under $SCHEMA_RESOURCE_DIR/ on classpath (openrune-central-common)"
        }
        dataSource.connection.use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                for (path in schemaFragments) {
                    val stream =
                        cl.getResourceAsStream(path)
                            ?: error("Missing classpath resource $path (openrune-central-common)")
                    val text =
                        stream.use { ins ->
                            BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8)).readText()
                        }
                    for (sql in splitPostgresStatements(text)) {
                        st.execute(sql)
                    }
                }
            }
        }
    }
}

/** Every `*.sql` directly under [SCHEMA_RESOURCE_DIR], sorted by filename (use numeric prefixes for order). */
private fun discoverSchemaSqlPaths(classLoader: ClassLoader): List<String> {
    val base =
        classLoader.getResource("$SCHEMA_RESOURCE_DIR/")
            ?: classLoader.getResource(SCHEMA_RESOURCE_DIR)
            ?: error("Missing classpath resource $SCHEMA_RESOURCE_DIR/ (openrune-central-common)")
    val prefix = "$SCHEMA_RESOURCE_DIR/"
    return when (base.protocol) {
        "file" -> {
            val dir = Paths.get(base.toURI())
            Files.list(dir).use { stream ->
                stream
                    .filter {
                        Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                            it.fileName.toString().endsWith(".sql", ignoreCase = true)
                    }
                    .map { "${prefix}${it.fileName}" }
                    .sorted()
                    .toList()
            }
        }
        "jar" -> {
            val conn = base.openConnection() as JarURLConnection
            conn.useCaches = false
            val jar = conn.jarFile
            val prefixNorm = prefix.replace('\\', '/')
            jar.entries().asSequence()
                .map { it.name }
                .filter { name ->
                    name.startsWith(prefixNorm) &&
                        name.endsWith(".sql", ignoreCase = true) &&
                        name.indexOf('/', prefixNorm.length) < 0
                }
                .sorted()
                .toList()
        }
        else -> error("Cannot list schema fragments under ${base.protocol}: $base")
    }
}

// Splits on ';' outside dollar-quoted bodies ($$ / $tag$) so PL/pgSQL survives.
internal fun splitPostgresStatements(raw: String): List<String> {
    val statements = ArrayList<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == ';' && !insideDollarQuote(raw, i)) {
            val stmt =
                sb.toString()
                    .trim()
                    .lines()
                    .filterNot { it.trim().startsWith("--") }
                    .joinToString("\n")
                    .trim()
            if (stmt.isNotEmpty()) {
                statements.add(stmt)
            }
            sb.clear()
            i++
            continue
        }
        sb.append(c)
        i++
    }
    val tail =
        sb.toString()
            .trim()
            .lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .trim()
    if (tail.isNotEmpty()) {
        statements.add(tail)
    }
    return statements
}

private fun insideDollarQuote(sql: String, index: Int): Boolean {
    var pos = 0
    var inQuote = false
    var marker = ""
    while (pos < index) {
        if (sql[pos] != '$') {
            pos++
            continue
        }
        if (!inQuote) {
            val endTag = sql.indexOf('$', pos + 1)
            if (endTag < 0) {
                return false
            }
            marker = sql.substring(pos, endTag + 1)
            inQuote = true
            pos = endTag + 1
        } else {
            if (sql.regionMatches(pos, marker, 0, marker.length)) {
                inQuote = false
                pos += marker.length
            } else {
                pos++
            }
        }
    }
    return inQuote
}
