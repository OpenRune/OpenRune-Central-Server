package dev.openrune.central.logging

import kotlinx.serialization.json.Json

/**
 * Shared JSON settings for sending/receiving Loggable messages.
 */
object LoggingJson {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            classDiscriminator = "logType"
        }
}

