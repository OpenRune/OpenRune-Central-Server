plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.bouncycastle:bcprov-jdk15to18:1.78.1")
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}
