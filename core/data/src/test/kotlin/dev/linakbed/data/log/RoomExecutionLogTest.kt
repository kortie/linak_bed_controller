package dev.linakbed.data.log

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.data.db.LinakDatabase
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.remote.RemoteChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomExecutionLogTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LinakDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @After
    fun close() = database.close()

    private fun entry(n: Int) =
        ExecutionEntry(
            at = Instant.ofEpochMilli(n * 1000L),
            source = if (n % 2 == 0) CommandSource.REMOTE else CommandSource.MANUAL,
            channel = if (n % 2 == 0) RemoteChannel.SMARTTHINGS_NOTIFICATION else null,
            requestId = if (n % 2 == 0) "req-$n" else null,
            action = "preset P${n % 4 + 1}",
            result = "executed",
            detail = if (n == 2) "#BED **** preset 3" else null,
        )

    @Test
    fun `every field round-trips, newest first`() =
        runTest {
            val log = RoomExecutionLog(database.executionLogDao())

            log.record(entry(1))
            log.record(entry(2))

            assertThat(log.recent(10).first()).containsExactly(entry(2), entry(1)).inOrder()
        }

    @Test
    fun `only the most recent entries are kept`() =
        runTest {
            val log = RoomExecutionLog(database.executionLogDao(), capacity = 200)

            (1..205).forEach { log.record(entry(it)) }

            val all = log.recent(1000).first()
            assertThat(all).hasSize(200)
            assertThat(all.first()).isEqualTo(entry(205))
            assertThat(all.last()).isEqualTo(entry(6))
        }

    @Test
    fun `recent respects the limit`() =
        runTest {
            val log = RoomExecutionLog(database.executionLogDao())
            (1..5).forEach { log.record(entry(it)) }

            assertThat(log.recent(2).first().map { it.at.toEpochMilli() }).containsExactly(5000L, 4000L).inOrder()
        }
}
