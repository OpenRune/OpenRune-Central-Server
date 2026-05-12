package dev.or2.central.server.net

internal class ConnectionPermit(
    private val onRelease: () -> Unit,
) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        onRelease()
    }
}