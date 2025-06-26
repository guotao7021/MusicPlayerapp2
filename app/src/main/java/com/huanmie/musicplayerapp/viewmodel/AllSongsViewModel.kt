package com.huanmie.musicplayerapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.huanmie.musicplayerapp.data.MusicRepository
import com.huanmie.musicplayerapp.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AllSongsViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = MusicRepository.getInstance(application)

    private val _allSongs = MutableLiveData<List<Song>>()
    val allSongs: LiveData<List<Song>> = _allSongs

    init {
        // You might want to call startScan here as well if you want to scan on app start
        // or load previously scanned songs from a repository/database.
        // If your MainActivity/HomeActivity handles initial permission requests,
        // you can call startScan after those are granted.
    }

    fun startScan(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = musicRepository.scanDeviceForSongs()
            _allSongs.postValue(songs) // Use postValue for LiveData updates from background threads
        }
    }

    // You might also have a refresh function
    fun refreshSongs(context: Context) {
        startScan(context)
    }
}