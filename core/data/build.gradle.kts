plugins {
    alias(libs.plugins.linakbed.android.library)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "dev.linakbed.data"
}

kapt {
    // Room 스키마를 저장소에 남겨 마이그레이션 검증에 쓴다 (S06).
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
}
