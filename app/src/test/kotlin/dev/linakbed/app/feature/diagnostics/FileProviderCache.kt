package dev.linakbed.app.feature.diagnostics

import androidx.core.content.FileProvider

/**
 * FileProvider는 경로 규칙을 정적 캐시에 둔다. Robolectric은 테스트마다 캐시 디렉터리가 달라
 * 앞 테스트의 경로가 남으면 getUriForFile이 실패하므로, 공유를 만드는 테스트 전에 비운다.
 */
fun resetFileProviderCache() {
    val field = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    (field.get(null) as MutableMap<*, *>).clear()
}
