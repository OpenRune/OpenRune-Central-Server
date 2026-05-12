package dev.or2.central

import dev.or2.central.util.config.applyKtorHttpPortFromCentralConfigBeforeEngineStart
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    applyKtorHttpPortFromCentralConfigBeforeEngineStart()
    EngineMain.main(args)
}
