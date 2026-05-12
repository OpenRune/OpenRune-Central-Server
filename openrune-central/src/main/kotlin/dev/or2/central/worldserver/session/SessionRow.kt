package dev.or2.central.worldserver.session

data class SessionRow(
    val id: Long,
    val accountId: Long,
    val worldId: Int,
    val tokenHash: ByteArray,
    val createdAt: Long,
    val lastSeenAt: Long,
)
