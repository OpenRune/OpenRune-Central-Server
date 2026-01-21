plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":central-shared"))
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

