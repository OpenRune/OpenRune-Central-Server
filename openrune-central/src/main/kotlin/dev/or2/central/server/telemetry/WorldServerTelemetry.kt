package dev.or2.central.server.telemetry

interface WorldServerTelemetry {
    fun recordInboundValidated(opcode: Int) {}

    fun recordInboundRejected(
        opcode: Int,
        reason: String,
    ) {}

    fun recordLoginSuccess() {}

    companion object {
        val None: WorldServerTelemetry = object : WorldServerTelemetry {}
    }
}
