plugins {
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:2.3.12"))

    implementation(project(":api"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // config + storage backends
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.mongodb:mongodb-driver-sync:5.2.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}
