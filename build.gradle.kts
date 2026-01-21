import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("maven-publish")
}

group = "dev.or2"
version = "0.1.0"

val publishingDir = System.getenv("HOSTING_DIRECTORY")
    ?: "D:\\OpenRune\\openrune-hosting"

allprojects {
    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "idea")

    group = rootProject.group
    version = rootProject.version

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
                url = uri(publishingDir)
            }
        }
    }
}
