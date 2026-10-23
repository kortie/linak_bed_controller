plugins {
    alias(libs.plugins.linakbed.android.library)
}

android {
    namespace = "dev.linakbed.ble"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":core:domain")))
}
