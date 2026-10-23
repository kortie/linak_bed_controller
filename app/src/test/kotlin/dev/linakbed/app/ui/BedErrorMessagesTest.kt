package dev.linakbed.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.GattStage
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.safety.SafetyReason
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BedErrorMessagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val allErrors: List<BedError> =
        listOf(
            BedError.NotPaired,
            BedError.BluetoothOff,
            BedError.PermissionMissing("android.permission.BLUETOOTH_CONNECT"),
            BedError.ConnectTimeout,
            BedError.Disconnected,
            BedError.WriteFailed(201),
            BedError.GattFailure(GattStage.WRITE, null),
        ) +
            CommandSource.entries.map { BedError.Busy(it) } +
            CommandSource.entries.map { BedError.Interrupted(it) } +
            SafetyReason.entries.map { BedError.SafetyStop(it) }

    private fun BedError.text(): String {
        val text = BedErrorMessages.of(this)
        return if (text.arg == null) context.getString(text.res) else context.getString(text.res, text.arg)
    }

    @Test
    fun `every error has a distinct non-empty Korean message`() {
        val messages = allErrors.map { it.text() }

        assertThat(messages.none { it.isBlank() }).isTrue()
        assertThat(messages.toSet()).hasSize(allErrors.size)
    }

    @Test
    fun `arguments are filled in`() {
        assertThat(BedError.WriteFailed(201).text()).contains("201")
        assertThat(BedError.GattFailure(GattStage.DISCOVER_SERVICES, 129).text()).contains("DISCOVER_SERVICES")
    }
}
