package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC, dateAdded DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    suspend fun isBookmarked(path: String): Boolean

    @Update
    suspend fun updateBookmarks(bookmarks: List<BookmarkEntity>)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedTimestamp DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items ORDER BY deletedTimestamp DESC")
    suspend fun getAllTrashList(): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(trash: TrashEntity): Long

    @Delete
    suspend fun deleteTrash(trash: TrashEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteTrashById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearTrash()
}

@Dao
interface FolderSortDao {
    @Query("SELECT * FROM folder_sort WHERE folderPath = :path")
    suspend fun getSortForFolder(path: String): FolderSortEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSortForFolder(folderSort: FolderSortEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearches()
}

@Dao
interface NetworkConnectionDao {
    @Query("SELECT * FROM network_connections ORDER BY dateAdded DESC")
    fun getAllConnections(): Flow<List<NetworkConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: NetworkConnectionEntity): Long

    @Update
    suspend fun updateConnection(connection: NetworkConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: NetworkConnectionEntity)
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT * FROM excluded_folders")
    fun getAllExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT * FROM excluded_folders")
    suspend fun getAllExcludedFoldersList(): List<ExcludedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcluded(excluded: ExcludedFolderEntity)

    @Delete
    suspend fun deleteExcluded(excluded: ExcludedFolderEntity)
}
