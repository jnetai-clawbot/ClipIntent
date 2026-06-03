package com.clipintent.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Simple SQLite database for storing clipboard history.
 * Room not used to minimize dependencies.
 */
class HistoryDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "clip_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_CLIPS = "clips"
        private const val COL_ID = "id"
        private const val COL_CONTENT = "content"
        private const val COL_TYPE = "type"
        private const val COL_TIMESTAMP = "timestamp"

        fun getInstance(context: Context): HistoryDatabase {
            return HistoryDatabase(context.applicationContext)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_CLIPS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CONTENT TEXT NOT NULL,
                $COL_TYPE TEXT NOT NULL DEFAULT 'TEXT',
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLIPS")
        onCreate(db)
    }

    /**
     * Insert a new clip into history.
     * Avoids duplicate consecutive insertions of the same content.
     */
    fun insertClip(content: String, type: String): Long {
        // Avoid inserting duplicate of the most recent entry
        val latest = getLatestContent()
        if (latest == content) return -1L

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CONTENT, content)
            put(COL_TYPE, type)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(TABLE_CLIPS, null, values)
    }

    /**
     * Get the most recent content string (for deduplication).
     */
    private fun getLatestContent(): String? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS, arrayOf(COL_CONTENT),
            null, null, null, null,
            "$COL_TIMESTAMP DESC", "1"
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    /**
     * Get all clips ordered by most recent first.
     */
    fun getAllClips(): List<ClipEntry> {
        val clips = mutableListOf<ClipEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS, null,
            null, null, null, null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                clips.add(
                    ClipEntry(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        content = it.getString(it.getColumnIndexOrThrow(COL_CONTENT)),
                        type = it.getString(it.getColumnIndexOrThrow(COL_TYPE)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                    )
                )
            }
        }
        return clips
    }

    /**
     * Search clips by content text.
     */
    fun searchClips(query: String): List<ClipEntry> {
        val clips = mutableListOf<ClipEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLIPS, null,
            "$COL_CONTENT LIKE ?",
            arrayOf("%$query%"),
            null, null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                clips.add(
                    ClipEntry(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        content = it.getString(it.getColumnIndexOrThrow(COL_CONTENT)),
                        type = it.getString(it.getColumnIndexOrThrow(COL_TYPE)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                    )
                )
            }
        }
        return clips
    }

    /**
     * Delete a single clip by ID.
     */
    fun deleteClip(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_CLIPS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /**
     * Clear all clips from history.
     */
    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_CLIPS, null, null)
    }

    /**
     * Data class representing a single clipboard entry.
     */
    data class ClipEntry(
        val id: Long,
        val content: String,
        val type: String,
        val timestamp: Long
    )
}