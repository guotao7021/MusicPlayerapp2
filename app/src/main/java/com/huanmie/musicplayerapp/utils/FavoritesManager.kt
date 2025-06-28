package com.huanmie.musicplayerapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.huanmie.musicplayerapp.data.Song

/**
 * 我的最爱管理器
 * 负责管理用户喜爱的歌曲
 */
class FavoritesManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _favoriteSongs = MutableLiveData<MutableList<Song>>()
    val favoriteSongs: LiveData<MutableList<Song>> = _favoriteSongs

    companion object {
        private const val PREFS_NAME = "favorites_prefs"
        private const val KEY_FAVORITE_SONGS = "favorite_songs"

        @Volatile
        private var INSTANCE: FavoritesManager? = null

        fun getInstance(context: Context): FavoritesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoritesManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    init {
        loadFavorites()
    }

    /**
     * 从SharedPreferences加载我的最爱歌曲
     */
    private fun loadFavorites() {
        val json = sharedPreferences.getString(KEY_FAVORITE_SONGS, null)
        val favorites: MutableList<Song> = if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<Song>>() {}.type
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        _favoriteSongs.value = favorites
    }

    /**
     * 保存我的最爱歌曲到SharedPreferences
     */
    private fun saveFavorites() {
        try {
            val json = gson.toJson(_favoriteSongs.value)
            sharedPreferences.edit()
                .putString(KEY_FAVORITE_SONGS, json)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 添加歌曲到我的最爱
     */
    fun addToFavorites(song: Song): Boolean {
        val currentFavorites = _favoriteSongs.value ?: mutableListOf()

        // 检查歌曲是否已经在我的最爱中
        if (currentFavorites.any { it.id == song.id }) {
            return false // 已经存在
        }

        currentFavorites.add(0, song) // 添加到列表开头
        _favoriteSongs.value = currentFavorites
        saveFavorites()
        return true
    }

    /**
     * 从我的最爱中移除歌曲
     */
    fun removeFromFavorites(song: Song): Boolean {
        val currentFavorites = _favoriteSongs.value ?: mutableListOf()
        val removed = currentFavorites.removeAll { it.id == song.id }

        if (removed) {
            _favoriteSongs.value = currentFavorites
            saveFavorites()
        }

        return removed
    }

    /**
     * 根据歌曲ID从我的最爱中移除歌曲
     */
    fun removeFromFavorites(songId: Long): Boolean {
        val currentFavorites = _favoriteSongs.value ?: mutableListOf()
        val removed = currentFavorites.removeAll { it.id == songId }

        if (removed) {
            _favoriteSongs.value = currentFavorites
            saveFavorites()
        }

        return removed
    }

    /**
     * 检查歌曲是否在我的最爱中
     */
    fun isFavorite(song: Song): Boolean {
        return _favoriteSongs.value?.any { it.id == song.id } ?: false
    }

    /**
     * 检查歌曲ID是否在我的最爱中
     */
    fun isFavorite(songId: Long): Boolean {
        return _favoriteSongs.value?.any { it.id == songId } ?: false
    }

    /**
     * 切换歌曲的我的最爱状态
     */
    fun toggleFavorite(song: Song): Boolean {
        return if (isFavorite(song)) {
            removeFromFavorites(song)
            false // 返回false表示已取消收藏
        } else {
            addToFavorites(song)
            true // 返回true表示已添加收藏
        }
    }

    /**
     * 获取我的最爱歌曲列表
     */
    fun getFavorites(): List<Song> {
        return _favoriteSongs.value ?: emptyList()
    }

    /**
     * 获取我的最爱歌曲数量
     */
    fun getFavoritesCount(): Int {
        return _favoriteSongs.value?.size ?: 0
    }

    /**
     * 清空我的最爱
     */
    fun clearFavorites() {
        _favoriteSongs.value = mutableListOf()
        saveFavorites()
    }

    /**
     * 刷新我的最爱数据
     */
    fun refresh() {
        loadFavorites()
    }
}