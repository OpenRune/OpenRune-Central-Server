import java.io.File
import org.gradle.api.publish.PublishingExtension

plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
}

val hostingDirectory: File =
    System.getenv("HOSTING_DIRECTORY")?.let { File(it) }
        ?: File("D:\\OpenRune\\openrune-hosting")

val buildNumber = System.getenv("BUILD_NUMBER") ?: "2.0.0"

val centralPublishModules =
    listOf(
        ":central-common",
        ":central-worldlink",
        ":central-app",
    )

subprojects {
    repositories {
        mavenCentral()
    }
    group = "dev.or2"
    version = buildNumber

    afterEvaluate {
        extensions.findByType<PublishingExtension>()?.repositories {
            maven {
                name = "hosting"
                url = hostingDirectory.toURI()
            }
        }
    }
}

group = "dev.or2"
version = buildNumber

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description =
        "Publishes dev.or2:central-common, dev.or2:central-worldlink, and dev.or2:openrune-central to ~/.m2."
    dependsOn(centralPublishModules.map { "$it:publishMavenJavaPublicationToMavenLocal" })
}

tasks.register("publishToHosting") {
    group = "publishing"
    description =
        "Publishes dev.or2:central-common, dev.or2:central-worldlink, and dev.or2:openrune-central " +
            "(thin library JARs + sources only) to the hosting Maven repo ($hostingDirectory). " +
            "Does not publish central-all, central-server, or fat -all JARs."
    dependsOn(centralPublishModules.map { "$it:publishMavenJavaPublicationToHostingRepository" })
}

// Backward-compatible aliases (old task names from prior Gradle scripts / IDE run configs).
tasks.register("publishCentralStackToMavenLocal") {
    dependsOn(tasks.named("publishToMavenLocal"))
}
tasks.register("publishCentralStackToHosting") {
    dependsOn(tasks.named("publishToHosting"))
}

val visiblePublishTasks = setOf("publishToMavenLocal", "publishToHosting")

gradle.projectsEvaluated {
    allprojects {
        tasks.configureEach {
            if (project === rootProject && name in visiblePublishTasks) {
                return@configureEach
            }
            val hide =
                name == "publish" ||
                    name == "publishToMavenLocal" ||
                    name == "publishAllPublicationsToHostingRepository" ||
                    (name.startsWith("publish") && name.contains("Publication")) ||
                    name.startsWith("generateMetadataFileFor") ||
                    name.startsWith("generatePomFileFor")
            if (hide) {
                group = null
            }
        }
    }
}

tasks.register("runCentral") {
    group = "application"
    description = "Runs the standalone OpenRune Central server"
    dependsOn(":central-app:run")
}

tasks.register("shadowCentralJar") {
    group = "build"
    description = "Builds the fat central-server.jar"
    dependsOn(":central-app:shadowJar")
}
