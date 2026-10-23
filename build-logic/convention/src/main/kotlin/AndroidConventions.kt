import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Android 모듈 공통: SDK, Java/Kotlin 타깃, lint, Robolectric 단위 테스트 (README A5: 로컬 단위 테스트만). */
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("minSdk").toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests.isIncludeAndroidResources = true
        }
        lint {
            abortOnError = true
            warningsAsErrors = true
            // 의존성·SDK 버전은 사내 미러에서 받을 수 있는 조합으로 고정하므로 "새 버전 있음" 경고는 끈다.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
            // 어노테이션 처리는 의도적으로 kapt를 쓴다: KSP2가 데몬 안에서 다른 모듈의 옛 jar를 보는 문제 (S05 검증 기록).
            disable += "KaptUsageInsteadOfKsp"
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(JVM_TARGET)
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }

    configureQuality(isAndroid = true)

    dependencies {
        "testImplementation"(libs.library("junit4"))
        "testImplementation"(libs.library("truth"))
        "testImplementation"(libs.library("robolectric"))
        "testImplementation"(libs.library("androidx-test-core"))
        "testImplementation"(libs.library("androidx-test-ext-junit"))
        "testImplementation"(libs.library("kotlinx-coroutines-test"))
    }
}
