package com.example.musicplayerapp2.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Playlist(
    val id: Long, // Fix: Changed from String to Long to match System.currentTimeMillis() and navArgs
    val name: String,
    val songs: MutableList<Song> // Fix: Changed from List<Song> to MutableList<Song> to allow adding/removing songs
) : Parcelable