import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** 순수 JVM 모듈 (core:protocol, core:domain). android.* 사용 시 컴파일 실패로 의존 방향을 강제한다. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-test-fixtures")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(JVM_TARGET)
                compilerOptions {
                    allWarningsAsErrors.set(true)
                }
            }

            configureQuality(isAndroid = false)

            extensions.configure<KoverProjectExtension> {
                // 테스트 대역(src/testFixtures)은 제품 코드가 아니므로 커버리지에서 뺀다.
                currentProject {
                    sources {
                        excludedSourceSets.add("testFixtures")
                    }
                }
                reports {
                    verify {
                        rule {
                            minBound(MIN_LINE_COVERAGE)
                        }
                    }
                }
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            dependencies {
                "testImplementation"(platform(libs.library("junit5-bom")))
                "testImplementation"(libs.library("junit5-jupiter"))
                "testRuntimeOnly"(libs.library("junit5-platform-launcher"))
                "testImplementation"(libs.library("truth"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
            }
        }
    }

    private companion object {
        const val MIN_LINE_COVERAGE = 85
    }
}
