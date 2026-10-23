package dev.linakbed.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** 앱 DB. 스키마는 core/data/schemas에 남긴다 — 버전을 올릴 때 마이그레이션과 그 테스트가 필수 (S06). */
@Database(entities = [ExecutionEntryEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class LinakDatabase : RoomDatabase() {
    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        private const val NAME = "linakbed.db"

        fun create(context: Context): LinakDatabase = Room.databaseBuilder(context, LinakDatabase::class.java, NAME).build()
    }
}
