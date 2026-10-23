package dev.linakbed.domain.port

import dev.linakbed.domain.log.ExecutionEntry
import kotlinx.coroutines.flow.Flow

interface ExecutionLog {
    suspend fun record(entry: ExecutionEntry)

    fun recent(limit: Int): Flow<List<ExecutionEntry>>
}
