package dev.linakbed.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "execution_log")
data class ExecutionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val source: String,
    val channel: String?,
    val requestId: String?,
    val action: String,
    val result: String,
    val detail: String?,
)
