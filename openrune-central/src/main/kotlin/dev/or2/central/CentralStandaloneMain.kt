package dev.or2.central

import dev.or2.central.util.config.loadCentralRuntimeConfig
import io.ktor.server.application.Application

fun Application.module() {
    installOpenRuneCentral(loadCentralRuntimeConfig())
}
