package dev.linakbed.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(entry: ExecutionEntryEntity)

    /** 최신 [keep]건만 남긴다. */
    @Query("DELETE FROM execution_log WHERE id NOT IN (SELECT id FROM execution_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Transaction
    suspend fun insertAndTrim(
        entry: ExecutionEntryEntity,
        keep: Int,
    ) {
        insert(entry)
        trim(keep)
    }

    @Query("SELECT * FROM execution_log ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ExecutionEntryEntity>>
}
