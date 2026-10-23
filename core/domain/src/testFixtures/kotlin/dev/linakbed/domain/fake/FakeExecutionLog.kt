package dev.linakbed.domain.fake

import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.port.ExecutionLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExecutionLog : ExecutionLog {
    private val all = MutableStateFlow<List<ExecutionEntry>>(emptyList())
    val entries: List<ExecutionEntry> get() = all.value

    override suspend fun record(entry: ExecutionEntry) {
        all.value = all.value + entry
    }

    override fun recent(limit: Int): Flow<List<ExecutionEntry>> = all.map { it.takeLast(limit).reversed() }
}
