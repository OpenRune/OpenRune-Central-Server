package dev.openrune.central.api

import java.nio.charset.StandardCharsets

/**
 * Canonical payload bytes to sign/verify for private API requests.
 *
 * Format:
 *   {timestamp}\n{worldId}\n{METHOD}\n{PATH}\n{BODY}
 */
fun buildPrivateAuthPayload(
    timestampMs: String,
    worldId: String,
    method: String,
    path: String,
    body: String
): ByteArray {
    return buildString {
        append(timestampMs).append('\n')
        append(worldId).append('\n')
        append(method).append('\n')
        append(path).append('\n')
        append(body)
    }.toByteArray(StandardCharsets.UTF_8)
}

