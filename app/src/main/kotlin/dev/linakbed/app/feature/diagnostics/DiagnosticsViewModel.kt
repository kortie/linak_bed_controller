package dev.linakbed.app.feature.diagnostics

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.di.IoDispatcher
import dev.linakbed.app.service.ServiceRemoteRequestDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

data class DiagnosticsUiState(
    val checks: Map<SystemCheck, Boolean> = emptyMap(),
    /** 마지막으로 폴백 경로로 실행한 시각 (있으면 경고). */
    val lastFallback: Instant? = null,
    val logDirectory: String = "",
    /** 공유 시트로 넘길 인텐트. 화면이 띄운 뒤 [DiagnosticsViewModel.onShareHandled]로 비운다. */
    val shareIntent: Intent? = null,
    val exportEmpty: Boolean = false,
)

/** 진단 (S07): 원격 연동 조건 · 백그라운드 실행 제한 경고 · 로그 내보내기 (ADR-007). */
@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        private val systemChecks: SystemChecks,
        private val exporter: LogExporter,
        dispatcher: ServiceRemoteRequestDispatcher,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DiagnosticsUiState(logDirectory = exporter.logDirectory.absolutePath))
        val uiState: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { dispatcher.lastFallback.collect { at -> mutableState.update { it.copy(lastFallback = at) } } }
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val checks = systemChecks.evaluate()
                mutableState.update { it.copy(checks = checks) }
            }
        }

        fun export() {
            viewModelScope.launch {
                val intent = withContext(io) { exporter.shareIntent() }
                mutableState.update { it.copy(shareIntent = intent, exportEmpty = intent == null) }
            }
        }

        fun onShareHandled() {
            mutableState.update { it.copy(shareIntent = null, exportEmpty = false) }
        }
    }
