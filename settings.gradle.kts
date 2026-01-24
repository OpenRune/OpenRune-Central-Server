rootProject.name = "openrune-central-server"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
        maven("https://jitpack.io")
    }
}

include(":api")
include(":api-client")
include(":api-server")


