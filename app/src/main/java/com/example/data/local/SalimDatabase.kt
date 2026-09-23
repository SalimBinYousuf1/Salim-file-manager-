package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        TrashEntity::class,
        FolderSortEntity::class,
        SearchHistoryEntity::class,
        NetworkConnectionEntity::class,
        ExcludedFolderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SalimDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun trashDao(): TrashDao
    abstract fun folderSortDao(): FolderSortDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun networkConnectionDao(): NetworkConnectionDao
    abstract fun excludedFolderDao(): ExcludedFolderDao

    companion object {
        @Volatile
        private var INSTANCE: SalimDatabase? = null

        fun getDatabase(context: Context): SalimDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SalimDatabase::class.java,
                    "salim_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
