package dev.linakbed.app.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.port.ExecutionLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExecutionLogViewModel
    @Inject
    constructor(
        executionLog: ExecutionLog,
    ) : ViewModel() {
        /** 최신순 최근 [LIMIT]건. */
        val entries: StateFlow<List<ExecutionEntry>> =
            executionLog.recent(LIMIT).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        companion object {
            const val LIMIT = 200
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
