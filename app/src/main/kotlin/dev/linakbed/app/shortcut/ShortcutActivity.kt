package dev.linakbed.app.shortcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 앱 바로가기의 중계 화면 (ADR-011). 화면 없이 명령을 원격 요청(`APP_SHORTCUT`)으로 넘기고 바로 닫힌다.
 * `exported=false` — 시스템(바로가기 실행)과 앱 자신만 시작할 수 있다 (02 문서 Part C2 출처 검사).
 */
@AndroidEntryPoint
class ShortcutActivity : ComponentActivity() {
    @Inject
    lateinit var forwarder: ShortcutForwarder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 회전 등으로 다시 만들어질 때 같은 명령을 두 번 보내지 않는다.
        if (savedInstanceState == null) forwarder.forward(intent?.data)
        finish()
    }
}
