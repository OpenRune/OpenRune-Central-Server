package dev.openrune.central.storage

/**
 * Shared naming rules so Mongo collections, Postgres tables, and FlatGson dirs stay consistent.
 */
object StorageNaming {
    fun bucketName(bucket: JsonBucket): String = bucket.name.lowercase()

    fun bucketSnake(bucket: JsonBucket): String = toSnakeCase(bucket.name)

    fun toSnakeCase(s: String): String {
        // Handles:
        // - ENUM_STYLE: PLAYER_SAVES -> player_saves
        // - CamelCase: PlayerLogin -> player_login
        // - Already snake: player_login -> player_login
        val out = StringBuilder()
        var prevWasUnderscore = false
        var prevWasLowerOrDigit = false
        for (ch in s) {
            when {
                ch == '-' || ch == ' ' -> {
                    if (!prevWasUnderscore && out.isNotEmpty()) out.append('_')
                    prevWasUnderscore = true
                    prevWasLowerOrDigit = false
                }
                ch == '_' -> {
                    if (!prevWasUnderscore && out.isNotEmpty()) out.append('_')
                    prevWasUnderscore = true
                    prevWasLowerOrDigit = false
                }
                ch.isUpperCase() -> {
                    if (prevWasLowerOrDigit && out.isNotEmpty()) out.append('_')
                    out.append(ch.lowercaseChar())
                    prevWasUnderscore = false
                    prevWasLowerOrDigit = false
                }
                ch.isLetterOrDigit() -> {
                    out.append(ch.lowercaseChar())
                    prevWasUnderscore = false
                    prevWasLowerOrDigit = ch.isLowerCase() || ch.isDigit()
                }
                else -> {
                    // drop
                }
            }
        }
        return out.toString().trim('_')
    }

    fun logPartitionName(logType: String): String = "${toSnakeCase(logType)}_logs"
}

