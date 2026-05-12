plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("com.github.node-gradle.node") version "7.1.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
    group = "dev.or2"
    version = "1.0-SNAPSHOT"
}

tasks.register("runCentral") {
    group = "application"
    description = "Runs the standalone OpenRune Central HTTP + world-link server"
    dependsOn(":openrune-central:run")
}

tasks.register("run") {
    group = "application"
    description = "Alias for runCentral (standalone Central server)"
    dependsOn("runCentral")
}

tasks.register("shadowCentralJar") {
    group = "build"
    description = "Builds the fat openrune-central-server.jar"
    dependsOn(":openrune-central:shadowJar")
}
