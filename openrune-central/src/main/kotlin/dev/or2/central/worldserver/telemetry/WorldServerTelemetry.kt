package dev.or2.central.worldserver.telemetry

import io.micrometer.core.instrument.MeterRegistry

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

class MicrometerWorldServerTelemetry(
    private val registry: MeterRegistry,
) : WorldServerTelemetry {
    override fun recordInboundValidated(opcode: Int) {
        registry.counter("world_link.inbound.validated").increment()
        registry.counter("world_link.inbound.by_opcode", "opcode", opcodeHex(opcode)).increment()
    }

    override fun recordInboundRejected(
        opcode: Int,
        reason: String,
    ) {
        registry.counter("world_link.inbound.rejected").increment()
        registry
            .counter(
                "world_link.inbound.reject.detail",
                "opcode",
                opcodeHex(opcode),
                "reason",
                reason,
            ).increment()
    }

    override fun recordLoginSuccess() {
        registry.counter("world_link.login.success").increment()
    }

    private fun opcodeHex(op: Int): String = "0x%02x".format(op)
}
