package dev.or2.central.server.session

data class SessionRow(
    val id: Long,
    val accountId: Long,
    val worldId: Int,
    val characterId: Int?,
    val tokenHash: ByteArray,
    val createdAt: Long,
    val lastSeenAt: Long,
)
