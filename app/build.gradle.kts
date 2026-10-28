import java.util.Properties

plugins {
    alias(libs.plugins.linakbed.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.linakbed.app"

    defaultConfig {
        applicationId = "com.juanstudio.bedcontrol"
        versionCode = 1
        versionName = "1.0.0"

        // 정보 화면 (ADR-010)
        buildConfigField("String", "DEVELOPER_NAME", "\"juanStudio\"")
        buildConfigField("String", "DEVELOPER_EMAIL", "\"kortie00@gmail.com\"")
        // 개인정보 처리방침 공개 URL (Play 등록 시 필요). 비어 있으면 정보 화면에 링크를 표시하지 않는다.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"\"")

        // 커피 한 잔 후원 (ADR-014): Play Console의 소모성 인앱 상품 ID. 바꾸면 Play Console 상품도 같이.
        buildConfigField("String", "DONATION_PRODUCT_ID", "\"coffee\"")
    }

    // Google Play 업로드 키 (docs/play/release-checklist.md). keystore.properties·키 파일은 커밋하지 않는다.
    // 파일이 없으면 release는 서명되지 않은 채로 빌드된다.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.reader().use(::load) }
        signingConfigs {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ble"))
    implementation(project(":core:data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.billing)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
