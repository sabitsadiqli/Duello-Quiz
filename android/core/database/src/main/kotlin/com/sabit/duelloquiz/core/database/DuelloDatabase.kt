package com.sabit.duelloquiz.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local database (mock exams first, EXAM-13). Server data of other features stays in the
 * per-account DataStore cache. Bump [version] with a migration for every schema change; the
 * exported schemas in `core/database/schemas` are the reference.
 */
@Database(
    entities = [
        ExamAttemptEntity::class,
        ExamAnswerEntity::class,
        ExamListeningEntity::class,
        ExamPassageOffsetEntity::class,
        ExamMarkEntity::class,
        ExamCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DuelloDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao
}
