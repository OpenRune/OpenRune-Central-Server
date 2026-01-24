plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("maven-publish")
}

val buildDirectory = System.getenv("HOSTING_DIRECTORY") ?: "D:\\openrune-hosting"
val buildNumber = "1.0.0"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "idea")

    group = "dev.or2.central"
    version = buildNumber

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name

                pom {
                    name.set("OpenRune - ${project.name}")
                    description.set("Module ${project.name} of the OpenRune project.")
                    url.set("https://github.com/OpenRune")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://opensource.org/licenses/Apache-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("openrune")
                            name.set("OpenRune Team")
                            email.set("contact@openrune.dev")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/OpenRune.git")
                        developerConnection.set("scm:git:ssh://github.com/OpenRune.git")
                        url.set("https://github.com/OpenRune")
                    }
                }
            }
        }

        repositories {
            maven {
                url = uri(buildDirectory)
            }
        }
    }
}

val apiVersion = buildNumber
val apiServerVersion = buildNumber
val apiClientVersion = buildNumber

group = "dev.or2.central"
version = buildNumber

publishing {
    publications {
        repositories {
            maven {
                url = uri(buildDirectory)
            }
        }

        create<MavenPublication>("server") {
            artifactId = "server"

            pom {
                name.set("OpenRune - server")
                description.set("Aggregate module including both the client api and implementation for game-server.")
                url.set("https://github.com/OpenRune/openrune-aggregates")

                withXml {
                    asNode().appendNode("dependencies").apply {
                        addDependency("dev.or2.central", "api", apiVersion)
                        addDependency("dev.or2.central", "api-server", apiServerVersion)
                    }
                }
            }
        }

        create<MavenPublication>("client") {
            artifactId = "client"

            pom {
                name.set("OpenRune - client")
                description.set("Aggregate module including both the api and client implementation for game-server.")
                url.set("https://github.com/OpenRune/openrune-aggregates")

                withXml {
                    asNode().appendNode("dependencies").apply {
                        addDependency("dev.or2.central", "api", apiVersion)
                        addDependency("dev.or2.central", "api-client", apiClientVersion)
                    }
                }
            }
        }
    }
}

private fun groovy.util.Node.addDependency(
    groupId: String,
    artifactId: String,
    version: String,
    scope: String = "compile",
): groovy.util.Node =
    appendNode("dependency").also { dep ->
        dep.appendNode("groupId", groupId)
        dep.appendNode("artifactId", artifactId)
        dep.appendNode("version", version)
        dep.appendNode("scope", scope)
    }