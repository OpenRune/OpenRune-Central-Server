import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.10"
    `java-library`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "openrune-central-common"
            pom {
                name.set("openrune-central-common")
                description.set(
                    "Shared OpenRune artifacts: PostgreSQL schema fragments under db/schema/ (applied in order by Central), " +
                        "classpath SQL under sql/ (incl. character_varps + character_attrs), " +
                        "OpenRuneSql loader (dev.or2.sql), and portable login types (dev.or2.login.model).",
                )
            }
        }
    }
}
