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
        val schemaFiles = discoverSchemaSqlPaths(cl)

        require(schemaFiles.isNotEmpty()) {
            "No *.sql files under $SCHEMA_RESOURCE_DIR/"
        }

        dataSource.connection.use { conn ->
            conn.autoCommit = true

            conn.createStatement().use { st ->
                for (path in schemaFiles) {
                    val text = cl.getResourceAsStream(path)?.use { ins ->
                        BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8)).readText()
                    } ?: error("Missing resource: $path")

                    splitPostgresStatements(text).forEach { st.execute(it) }
                }
            }
        }
    }
}

private fun discoverSchemaSqlPaths(classLoader: ClassLoader): List<String> {
    val base =
        classLoader.getResource("$SCHEMA_RESOURCE_DIR/")
            ?: classLoader.getResource(SCHEMA_RESOURCE_DIR)
            ?: error("Missing schema directory: $SCHEMA_RESOURCE_DIR")

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
                    .map { prefix + it.fileName.toString() }
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

        else -> error("Unsupported protocol: ${base.protocol}")
    }
}

internal fun splitPostgresStatements(raw: String): List<String> {
    val statements = mutableListOf<String>()
    val sb = StringBuilder()

    var inDollarQuote = false
    var dollarTag = ""
    var i = 0

    fun flush() {
        val stmt = sb.toString()
            .replace("--.*".toRegex(), "") // single-line comments
            .trim()

        if (stmt.isNotEmpty()) {
            statements.add(stmt)
        }
        sb.clear()
    }

    while (i < raw.length) {
        val c = raw[i]

        if (c == '$') {
            val end = raw.indexOf('$', i + 1)

            if (end > i) {
                val tag = raw.substring(i, end + 1)

                if (!inDollarQuote) {
                    inDollarQuote = true
                    dollarTag = tag
                } else if (tag == dollarTag) {
                    inDollarQuote = false
                    dollarTag = ""
                }

                sb.append(tag)
                i = end + 1
                continue
            }
        }

        if (c == ';' && !inDollarQuote) {
            flush()
            i++
            continue
        }

        sb.append(c)
        i++
    }

    flush()
    return statements
}