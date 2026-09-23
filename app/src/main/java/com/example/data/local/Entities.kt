package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sortOrder: Int = 0,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val deletedTimestamp: Long = System.currentTimeMillis(),
    val mimeType: String = ""
)

@Entity(tableName = "folder_sort")
data class FolderSortEntity(
    @PrimaryKey val folderPath: String,
    val sortField: String,
    val isAscending: Boolean,
    val foldersFirst: Boolean
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_connections")
data class NetworkConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String, // SMB, FTP, SFTP, WEBDAV
    val host: String,
    val port: Int,
    val username: String,
    val passwordEncrypted: String = "",
    val keyFilePath: String = "",
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val path: String,
    val dateAdded: Long = System.currentTimeMillis()
)
