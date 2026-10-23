plugins {
    alias(libs.plugins.linakbed.jvm.library)
}

dependencies {
    // 언어 독립 프로토콜 벡터(linak-protocol-vectors.json)를 읽는 데만 쓴다 (ADR-006).
    testImplementation(libs.kotlinx.serialization.json)
}
