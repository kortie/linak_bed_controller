pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "linak-bed-controller"

include(
    ":core:protocol",
    ":core:domain",
    ":core:ble",
    ":core:data",
    ":integration:smartthings",
    ":app",
)
