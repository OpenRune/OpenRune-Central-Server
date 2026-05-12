package dev.or2.central.worldserver.session

sealed class WorldServerHandleResult {
    data class Reply(
        val payload: ByteArray,
        val closeAfterWrite: Boolean = false,
    ) : WorldServerHandleResult()

    data object CloseSilent : WorldServerHandleResult()
}
