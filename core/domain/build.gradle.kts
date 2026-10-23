plugins {
    alias(libs.plugins.linakbed.jvm.library)
}

dependencies {
    api(project(":core:protocol"))
    api(libs.kotlinx.coroutines.core)
}
