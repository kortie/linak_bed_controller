package dev.linakbed.app.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.linakbed.app.R

/**
 * 화면 오른쪽 위의 작은 언어 전환 버튼. 한국어 화면에는 "EN", 영어 화면에는 "한국어"를 보여 준다.
 * 앱 언어는 앱별 언어 설정(Android 13+, minSdk 34)으로 바꾸고, 시스템이 화면을 다시 만든다.
 */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val english = LocalConfiguration.current.locales[0].language == LANG_EN
    val description = stringResource(R.string.language_change)
    TextButton(
        onClick = { context.setAppLanguage(if (english) LANG_KO else LANG_EN) },
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(if (english) "한국어" else "EN", style = MaterialTheme.typography.labelLarge)
    }
}

private fun Context.setAppLanguage(tag: String) {
    getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
}

private const val LANG_KO = "ko"
private const val LANG_EN = "en"
