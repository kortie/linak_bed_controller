package dev.linakbed.app.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.feature.diagnostics.SystemCheck
import dev.linakbed.app.feature.diagnostics.SystemChecks
import dev.linakbed.app.feature.diagnostics.missingForRemote
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import dev.linakbed.domain.remote.notification.ChannelResult
import dev.linakbed.domain.remote.notification.IncomingNotification
import dev.linakbed.domain.remote.notification.NotificationChannel
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * SmartThings 루틴 연동 설정 (S07). 켜기 전에 시스템 조건을 확인하고, 모자라면 켜지 않고 안내한다.
 * 문구 테스터는 실제 채널 로직으로 해석만 하고 **기록하거나 침대를 움직이지 않는다**.
 */
@HiltViewModel
class RemoteSettingsViewModel
    @Inject
    constructor(
        private val remoteStore: RemoteSettingsStore,
        private val channelStore: NotificationChannelSettingsStore,
        presetNames: PresetNameStore,
        private val systemChecks: SystemChecks,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(RemoteSettingsUiState())
        val uiState: StateFlow<RemoteSettingsUiState> = mutableState.asStateFlow()

        private val tester = NotificationChannel(channelStore, presetNames, NoRecordHandler)
        private var enableRequested = false

        init {
            viewModelScope.launch { remoteStore.settings().collect { remote -> mutableState.update { it.copy(remote = remote) } } }
            viewModelScope.launch {
                val channel = channelStore.settings().first()
                mutableState.update { it.copy(channel = channel, prefixInput = channel.prefix, pinInput = channel.pin.orEmpty()) }
                channelStore.settings().collect { latest -> mutableState.update { it.copy(channel = latest) } }
            }
            refreshChecks()
        }

        fun onAction(action: RemoteSettingsAction) {
            when (action) {
                is RemoteSettingsAction.SetEnabled -> setEnabled(action.enabled)
                RemoteSettingsAction.RefreshChecks -> refreshChecks()
                is RemoteSettingsAction.PrefixChanged -> mutableState.update { it.copy(prefixInput = action.text, prefixInvalid = false) }
                is RemoteSettingsAction.PinChanged -> mutableState.update { it.copy(pinInput = action.text, pinInvalid = false) }
                RemoteSettingsAction.SaveChannel -> saveChannel()
                is RemoteSettingsAction.DelaySelected ->
                    viewModelScope.launch { remoteStore.update { it.copy(preExecutionDelay = action.seconds.seconds) } }
                is RemoteSettingsAction.NotifyOnSuccess ->
                    viewModelScope.launch {
                        remoteStore.update {
                            it.copy(
                                notifyOnSuccess = action.enabled,
                            )
                        }
                    }
                is RemoteSettingsAction.DismissHandled ->
                    viewModelScope.launch { channelStore.update { it.copy(dismissHandledNotification = action.enabled) } }
                is RemoteSettingsAction.TesterInputChanged ->
                    mutableState.update {
                        it.copy(
                            testerInput = action.text,
                            testerResult = null,
                        )
                    }
                RemoteSettingsAction.RunTester -> runTester()
            }
        }

        private fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                if (!enabled) {
                    enableRequested = false
                    mutableState.update { it.copy(missing = emptyList()) }
                    remoteStore.update { it.copy(enabled = false) }
                    return@launch
                }
                enableRequested = true
                tryEnable()
            }
        }

        private fun refreshChecks() {
            viewModelScope.launch {
                val checks = systemChecks.evaluate()
                mutableState.update { it.copy(checks = checks) }
                if (enableRequested) tryEnable(checks)
            }
        }

        /** 모자란 조건이 없으면 켠다. 시스템 설정에서 돌아와 다 갖춰졌으면 자동으로 켜진다. */
        private suspend fun tryEnable(checks: Map<SystemCheck, Boolean>? = null) {
            val current = checks ?: systemChecks.evaluate()
            val missing = current.missingForRemote(remoteStore.settings().first().preExecutionDelay)
            mutableState.update { it.copy(checks = current, missing = missing) }
            if (missing.isEmpty()) {
                enableRequested = false
                remoteStore.update { it.copy(enabled = true) }
            }
        }

        private fun saveChannel() {
            val state = uiState.value
            val prefix = state.prefixInput.trim()
            val pin = state.pinInput.trim().ifEmpty { null }
            val prefixInvalid = prefix.isEmpty() || prefix.any(Char::isWhitespace)
            val pinInvalid = pin != null && !NotificationChannelSettings.PIN_PATTERN.matches(pin)
            mutableState.update { it.copy(prefixInvalid = prefixInvalid, pinInvalid = pinInvalid) }
            if (prefixInvalid || pinInvalid) return
            viewModelScope.launch { channelStore.update { it.copy(prefix = prefix, pin = pin) } }
        }

        private fun runTester() {
            val text = uiState.value.testerInput
            viewModelScope.launch {
                val probe =
                    IncomingNotification(
                        packageName = NotificationChannelSettings.SMARTTHINGS_PACKAGE,
                        key = TESTER_KEY,
                        postedAt = Instant.EPOCH,
                        sentAt = null,
                        texts = listOf(text),
                    )
                val result =
                    when (val translated = tester.translate(probe)) {
                        ChannelResult.Ignore -> TesterResult.NotACommand
                        is ChannelResult.Rejected -> TesterResult.Rejected(translated.reason)
                        is ChannelResult.Request -> TesterResult.Command(translated.request.command)
                    }
                mutableState.update { it.copy(testerResult = result) }
            }
        }

        /** 테스터 전용: 아무것도 기록·실행하지 않는다. */
        private object NoRecordHandler : RemoteCommandHandler {
            override suspend fun handle(request: RemoteRequest): RemoteOutcome =
                RemoteOutcome(request.requestId, RemoteResult.Rejected(RejectReason.INVALID))

            override suspend fun reject(
                channel: RemoteChannel,
                requestId: String,
                reason: RejectReason,
                detail: String?,
            ) = Unit
        }

        private companion object {
            const val TESTER_KEY = "tester"
        }
    }
