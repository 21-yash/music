package com.musicsportsapp.data.local.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "dummy_entity")
data class DummyEntity(
    @PrimaryKey val id: Int = 0
)

/**
 * Room database.
 *
 * Currently has no real entities — they will be added as features are
 * built in later phases (user library, offline caching, etc.).
 * A DummyEntity is included to satisfy Room's compilation requirement.
 */
@Database(
    entities = [DummyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase()
