package com.example.musicplayerapp2.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.musicplayerapp2.data.Song // Make sure this import is present if not already

// PlayerViewModel，用于管理 PlayerActivity 的 UI 相关数据
class PlayerViewModel : ViewModel() {

    // 当前播放歌曲的 LiveData
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    // 当前播放列表的 LiveData
    private val _currentPlaylist = MutableLiveData<ArrayList<Song>>()
    val currentPlaylist: LiveData<ArrayList<Song>> = _currentPlaylist

    // 当前播放歌曲在播放列表中的索引
    private val _currentSongIndex = MutableLiveData<Int>()
    val currentSongIndex: LiveData<Int> = _currentSongIndex

    // LiveData for playback state, position, and song duration should ideally be handled directly by MusicService's LiveData to PlayerActivity.
    // However, if PlayerActivity still wants to manage some state derived from MusicService, these could be updated from PlayerActivity.
    // For now, let's keep them here as placeholders if you were using them elsewhere.
    // The previous design was trying to mirror MusicService's LiveData via MusicDataHolder which is fine,
    // but direct observation of MusicService's LiveData in PlayerActivity is more direct.

    // 更新当前歌曲
    fun setCurrentSong(song: Song) {
        _currentSong.value = song
    }

    // 更新当前播放列表和歌曲索引
    fun setCurrentPlaylist(playlist: ArrayList<Song>, index: Int) {
        _currentPlaylist.value = playlist
        _currentSongIndex.value = index
    }
}