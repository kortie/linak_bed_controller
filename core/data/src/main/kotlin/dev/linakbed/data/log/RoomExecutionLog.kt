package dev.linakbed.data.log

import android.content.Context
import dev.linakbed.data.db.ExecutionEntryEntity
import dev.linakbed.data.db.ExecutionLogDao
import dev.linakbed.data.db.LinakDatabase
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.remote.RemoteChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 사용자에게 보이는 실행 기록. 최근 [capacity]건만 보관한다. */
class RoomExecutionLog(
    private val dao: ExecutionLogDao,
    private val capacity: Int = DEFAULT_CAPACITY,
) : ExecutionLog {
    override suspend fun record(entry: ExecutionEntry) {
        dao.insertAndTrim(entry.toEntity(), capacity)
    }

    /** 최신 기록부터. */
    override fun recent(limit: Int): Flow<List<ExecutionEntry>> = dao.recent(limit).map { rows -> rows.map { it.toEntry() } }

    private fun ExecutionEntry.toEntity() =
        ExecutionEntryEntity(
            at = at,
            source = source.name,
            channel = channel?.name,
            requestId = requestId,
            action = action,
            result = result,
            detail = detail,
        )

    private fun ExecutionEntryEntity.toEntry() =
        ExecutionEntry(
            at = at,
            source = CommandSource.valueOf(source),
            channel = channel?.let(RemoteChannel::valueOf),
            requestId = requestId,
            action = action,
            result = result,
            detail = detail,
        )

    companion object {
        const val DEFAULT_CAPACITY = 200

        /** 앱 DB를 열어 만든다 (앱은 Room을 몰라도 된다). DI에서 싱글턴으로 한 번만. */
        fun create(context: Context): RoomExecutionLog = RoomExecutionLog(LinakDatabase.create(context).executionLogDao())
    }
}
