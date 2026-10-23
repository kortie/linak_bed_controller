package dev.linakbed.app.log

import android.content.Context
import java.io.File

/**
 * 로그 디렉터리 (ADR-007): `/sdcard/Android/data/com.juanstudio.linakbed/files/logs/`.
 * 권한 없이 쓸 수 있고 `adb pull`로 가져올 수 있다. 외부 저장소를 쓸 수 없으면 앱 내부 저장소로 대체한다.
 * (`/data/log`는 시스템 전용이라 앱이 쓸 수 없다.)
 */
object LogDirectory {
    private const val NAME = "logs"

    fun resolve(context: Context): File = context.getExternalFilesDir(NAME) ?: File(context.filesDir, NAME)
}
