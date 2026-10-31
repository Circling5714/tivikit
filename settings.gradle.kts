pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "tv-sach"
include(":app")
include(":khoa")
include(":kenh")
include(":hengio")
include(":cast")
include(":mapvoice")
include(":teleremote")
include(":go")
include(":launcher")
include(":trentv")
