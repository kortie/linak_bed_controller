package dev.linakbed.app.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.linakbed.app.BuildConfig
import dev.linakbed.app.R
import dev.linakbed.app.ui.theme.LinakBedTheme

/** 화면에 보일 앱·개발자 정보. 기본값은 BuildConfig (ADR-010). */
data class AboutInfo(
    val version: String = BuildConfig.VERSION_NAME,
    val developerName: String = BuildConfig.DEVELOPER_NAME,
    val developerEmail: String = BuildConfig.DEVELOPER_EMAIL,
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL,
)

/** 앱 밖으로 넘기는 동작 (테스트에서 바꿔 끼운다). */
interface AboutActions {
    fun open(uri: String)

    fun email(address: String)
}

@Composable
fun AboutScreen(
    info: AboutInfo = AboutInfo(),
    donationViewModel: DonationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val donation by donationViewModel.uiState.collectAsStateWithLifecycle()
    AboutContent(
        info = info,
        donation = donation,
        onDonate = { activity?.let(donationViewModel::donate) },
        actions = rememberAboutActions(context),
    )
}

@Composable
fun AboutContent(
    info: AboutInfo,
    donation: DonationUiState,
    onDonate: () -> Unit,
    actions: AboutActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_version, info.version), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_unofficial), style = MaterialTheme.typography.bodySmall)
        donation.price?.let { price -> DonationCard(price, donation.status, onDonate) }
        HorizontalDivider()
        Section(stringResource(R.string.about_developer)) {
            if (info.developerName.isNotBlank()) Text(info.developerName, style = MaterialTheme.typography.bodyLarge)
            if (info.developerEmail.isNotBlank()) {
                TextButton(onClick = { actions.email(info.developerEmail) }) { Text(info.developerEmail) }
            }
        }
        Section(stringResource(R.string.about_privacy)) {
            Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)
            if (info.privacyPolicyUrl.isNotBlank()) {
                TextButton(onClick = { actions.open(info.privacyPolicyUrl) }) { Text(stringResource(R.string.about_privacy_policy)) }
            }
        }
        Section(stringResource(R.string.about_licenses)) {
            Text(stringResource(R.string.about_licenses_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { actions.open(APACHE_LICENSE_URL) }) { Text(stringResource(R.string.about_licenses_apache)) }
        }
    }
}

@Composable
private fun DonationCard(
    price: String,
    status: DonationStatus,
    onDonate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().testTag(DONATION_CARD_TAG),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.about_donation_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_donation_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDonate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_donation_button, price))
            }
            statusMessage(status)?.let { message ->
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (status ==
                            DonationStatus.FAILED
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                )
            }
        }
    }
}

private fun statusMessage(status: DonationStatus): Int? =
    when (status) {
        DonationStatus.IDLE -> null
        DonationStatus.THANKS -> R.string.about_donation_thanks
        DonationStatus.PENDING -> R.string.about_donation_pending
        DonationStatus.FAILED -> R.string.about_donation_failed
    }

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun rememberAboutActions(context: Context): AboutActions {
    val noApp = stringResource(R.string.about_no_app)
    return object : AboutActions {
        override fun open(uri: String) = start(Intent(Intent.ACTION_VIEW, uri.toUri()))

        override fun email(address: String) = start(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))

        private fun start(intent: Intent) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (
                @Suppress("SwallowedException") missing: ActivityNotFoundException,
            ) {
                Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

const val DONATION_CARD_TAG = "donation_card"
private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Preview(showBackground = true)
@Composable
private fun AboutPreview() {
    LinakBedTheme {
        AboutContent(
            info =
                AboutInfo(
                    version = "1.0.0",
                    developerName = "juanStudio",
                    developerEmail = "kortie00@gmail.com",
                    privacyPolicyUrl = "",
                ),
            donation = DonationUiState(price = "₩1,500"),
            onDonate = {},
            actions =
                object : AboutActions {
                    override fun open(uri: String) = Unit

                    override fun email(address: String) = Unit
                },
        )
    }
}
