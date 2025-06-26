package com.huanmie.musicplayerapp.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository private constructor(private val context: Context) { // Make constructor private for singleton pattern

    private val sharedPreferences = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PLAYLISTS = "playlists"

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Function to scan device for songs
    suspend fun scanDeviceForSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, // Duration from MediaStore is typically Long (milliseconds)
            MediaStore.Audio.Media.DATA // Path to the file
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知标题"
                val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn)

                val contentUri: android.net.Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val albumArtUri: String? = try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, contentUri)
                    val art = retriever.embeddedPicture
                    retriever.release()
                    if (art != null) {
                        contentUri.toString()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Error getting album art for $title: ${e.message}")
                    null
                }

                // 第92行：确保这里传入的参数类型和顺序与 Song 类的构造函数匹配
                songs.add(Song(id, title, artist, duration, path, albumArtUri))
            }
        }
        songs
    }

    fun savePlaylistsToPrefs(playlists: List<Playlist>) {
        val json = gson.toJson(playlists)
        sharedPreferences.edit().putString(KEY_PLAYLISTS, json).apply()
        Log.d("MusicRepository", "Playlists saved: $json")
    }

    fun loadPlaylistsFromPrefs(): List<Playlist> {
        val json = sharedPreferences.getString(KEY_PLAYLISTS, null)
        Log.d("MusicRepository", "Loading playlists: $json")
        return if (json != null) {
            val type = object : TypeToken<List<Playlist>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
}