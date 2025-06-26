package com.huanmie.musicplayerapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.huanmie.musicplayerapp.data.Playlist
import com.huanmie.musicplayerapp.data.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 增强版播放列表 ViewModel，支持完整的播放列表管理功能
 */
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(
        "playlists_prefs",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<Playlist>>() {}.type

    private val _playlists = MutableLiveData<MutableList<Playlist>>(mutableListOf())
    val playlists: LiveData<MutableList<Playlist>> = _playlists

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString("playlists_json", null)
        val loaded: MutableList<Playlist> = if (!json.isNullOrEmpty()) {
            try {
                gson.fromJson(json, listType)
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        _playlists.value = loaded
    }

    private fun saveToPrefs() {
        val json = gson.toJson(_playlists.value)
        prefs.edit().putString("playlists_json", json).apply()
    }

    /**
     * 创建一个新的播放列表
     */
    fun createPlaylist(name: String) {
        val current = _playlists.value ?: mutableListOf()
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            songs = mutableListOf()
        )
        current.add(newPlaylist)
        _playlists.value = current
        saveToPrefs()
    }

    /**
     * 重命名播放列表
     */
    fun renamePlaylist(playlistId: Long, newName: String): Boolean {
        val list = _playlists.value ?: return false
        val target = list.find { it.id == playlistId } ?: return false

        // 检查新名称是否为空或与其他播放列表重复
        if (newName.trim().isEmpty()) return false
        if (list.any { it.id != playlistId && it.name == newName.trim() }) return false

        // 更新名称
        val updatedPlaylist = target.copy(name = newName.trim())
        val index = list.indexOf(target)
        list[index] = updatedPlaylist

        _playlists.value = list
        saveToPrefs()
        return true
    }

    /**
     * 删除播放列表
     */
    fun deletePlaylist(playlistId: Long): Boolean {
        val current = _playlists.value?.toMutableList() ?: mutableListOf()
        val target = current.find { it.id == playlistId } ?: return false

        if (current.remove(target)) {
            _playlists.value = current
            saveToPrefs()
            return true
        }
        return false
    }

    /**
     * 向指定播放列表添加歌曲
     */
    fun addSongToPlaylist(song: Song, playlistId: Long): Boolean {
        val list = _playlists.value ?: return false
        val target = list.find { it.id == playlistId } ?: return false

        // 检查歌曲是否已存在
        if (target.songs.none { it.id == song.id }) {
            target.songs.add(song)
            _playlists.value = list // 触发观察者
            saveToPrefs()
            return true
        }
        return false
    }

    /**
     * 从指定播放列表移除歌曲
     */
    fun removeSongFromPlaylist(songId: Long, playlistId: Long): Boolean {
        val list = _playlists.value ?: return false
        val target = list.find { it.id == playlistId } ?: return false

        val songToRemove = target.songs.find { it.id == songId }
        if (songToRemove != null && target.songs.remove(songToRemove)) {
            _playlists.value = list // 触发观察者
            saveToPrefs()
            return true
        }
        return false
    }

    /**
     * 批量从播放列表中删除歌曲
     */
    fun removeSongsFromPlaylist(songIds: List<Long>, playlistId: Long): Boolean {
        val list = _playlists.value ?: return false
        val target = list.find { it.id == playlistId } ?: return false

        var removed = false
        songIds.forEach { songId ->
            val songToRemove = target.songs.find { it.id == songId }
            if (songToRemove != null && target.songs.remove(songToRemove)) {
                removed = true
            }
        }

        if (removed) {
            _playlists.value = list
            saveToPrefs()
        }
        return removed
    }

    /**
     * 添加到默认 "Favorites" 列表
     */
    fun addToFavorites(song: Song): Boolean {
        val list = _playlists.value ?: mutableListOf()
        val fav = list.find { it.name == "Favorites" }
            ?: Playlist(
                id = System.currentTimeMillis(),
                name = "Favorites",
                songs = mutableListOf()
            ).also { list.add(it) }

        if (fav.songs.none { it.id == song.id }) {
            fav.songs.add(song)
            _playlists.value = list
            saveToPrefs()
            return true
        }
        return false
    }

    /**
     * 从默认 "Favorites" 列表移除
     */
    fun removeFromFavorites(song: Song): Boolean {
        val list = _playlists.value ?: return false
        val fav = list.find { it.name == "Favorites" } ?: return false

        if (fav.songs.remove(song)) {
            _playlists.value = list
            saveToPrefs()
            return true
        }
        return false
    }

    /**
     * 根据 ID 获取播放列表
     */
    fun getPlaylistById(id: Long): Playlist? {
        return _playlists.value?.find { it.id == id }
    }

    /**
     * 检查播放列表名称是否可用
     */
    fun isPlaylistNameAvailable(name: String, excludeId: Long? = null): Boolean {
        val list = _playlists.value ?: return true
        return list.none {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != excludeId
        }
    }

    /**
     * 获取播放列表中歌曲数量
     */
    fun getPlaylistSongCount(playlistId: Long): Int {
        return getPlaylistById(playlistId)?.songs?.size ?: 0
    }

    /**
     * 检查歌曲是否在播放列表中
     */
    fun isSongInPlaylist(songId: Long, playlistId: Long): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        return playlist.songs.any { it.id == songId }
    }
}