package dev.or2.central.util.config

data class WorldServerTcpConfig(
    val soBacklog: Int = 512,
    val readTimeoutSeconds: Int = 120,
    val maxConnectionsPerIp: Int = 32,
    val maxConnectionsTotal: Int = 4096,
    val maxFramesPerSecond: Double = 80.0,
    val maxFrameBurst: Double = 120.0,
)