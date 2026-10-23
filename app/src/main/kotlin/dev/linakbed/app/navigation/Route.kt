package dev.linakbed.app.navigation

import androidx.annotation.StringRes
import dev.linakbed.app.R

/** 홈에서 들어가는 화면 (ADR-009). 하단 탭은 없다. */
object Route {
    const val HOME = "home"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
}

/** 설정 아래 화면. */
enum class SettingsRoute(
    val route: String,
    @param:StringRes val title: Int,
) {
    REMOTE("settings/remote", R.string.settings_remote),
    GUIDE("settings/guide", R.string.settings_guide),
    LOG("settings/log", R.string.settings_log),
    DIAGNOSTICS("settings/diagnostics", R.string.settings_diagnostics),
}
