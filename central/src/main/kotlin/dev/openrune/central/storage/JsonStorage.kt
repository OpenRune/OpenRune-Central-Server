package dev.openrune.central.storage

interface JsonStorage : AutoCloseable {
    /**
     * For entity-style documents (punishments, player saves, login details, etc).
     * The input is raw JSON.
     */
    suspend fun upsert(bucket: JsonBucket, id: String, json: String)

    suspend fun get(bucket: JsonBucket, id: String): String?

    /**
     * For time-series/log style documents.
     */
    suspend fun append(bucket: JsonBucket, json: String)

    /**
     * Same as [append], but allows partitioning within a bucket (e.g. logs per logType).
     *
     * Example:
     * - bucket=LOGS, partition="PlayerLogin" -> player_login_logs.json / player_login_logs collection/table.
     */
    suspend fun append(bucket: JsonBucket, partition: String, json: String)
}

