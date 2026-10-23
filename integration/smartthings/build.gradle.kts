plugins {
    alias(libs.plugins.linakbed.android.library)
}

android {
    namespace = "dev.linakbed.smartthings"
}

dependencies {
    // 알림에서 텍스트를 뽑아 도메인 채널 층에 넘기는 일만 한다. BLE·Room에 직접 접근하지 않는다 (아키텍처 §4).
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":core:domain")))
}
