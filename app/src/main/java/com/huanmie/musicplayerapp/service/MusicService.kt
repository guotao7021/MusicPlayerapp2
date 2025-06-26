package com.huanmie.musicplayerapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.huanmie.musicplayerapp.MainActivity
import com.huanmie.musicplayerapp.PlayerActivity
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.data.Song
import java.util.Timer
import java.util.TimerTask

class MusicService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()

    // 播放列表和索引
    private var currentPlaylist: ArrayList<Song> = arrayListOf()
    private var currentSongIndex: Int = 0

    // 播放状态
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.OFF

    // LiveData 用于观察
    val currentSong      = MutableLiveData<Song?>()
    val isPlaying        = MutableLiveData<Boolean>()
    val playbackPosition = MutableLiveData<Int>()
    val isShuffleLiveData  = MutableLiveData<Boolean>().apply { postValue(isShuffle) }
    val repeatModeLiveData = MutableLiveData<RepeatMode>().apply { postValue(repeatMode) }

    private var playbackPositionTimer: Timer? = null


    companion object {
        const val EXTRA_SONG_LIST = "com.example.musicplayerapp2.service.extra.SONG_LIST"
        const val EXTRA_SONG_INDEX = "com.example.musicplayerapp2.service.extra.SONG_INDEX"
        const val ACTION_PLAY = "com.example.musicplayerapp2.service.action.PLAY"
        const val ACTION_PAUSE = "com.example.musicplayerapp2.service.action.PAUSE"
        const val ACTION_NEXT = "com.example.musicplayerapp2.service.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayerapp2.service.action.PREVIOUS"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_player_channel"
    }

    enum class RepeatMode {
        OFF, ONE, ALL
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        isPlaying.postValue(false)
        isShuffleLiveData.postValue(isShuffle)
        repeatModeLiveData.postValue(repeatMode)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY -> resumeSong()
            ACTION_PAUSE -> pauseSong()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
    }

    // 设置播放列表和开始播放
    fun setPlaylistAndPlay(playlist: ArrayList<Song>, index: Int) {
        currentPlaylist = playlist
        currentSongIndex = index.coerceIn(0, playlist.size - 1)
        if (playlist.isNotEmpty()) {
            playSong(playlist[currentSongIndex])
        }
    }

    fun playSong(song: Song) {
        currentSong.postValue(song)
        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(song.data)
                prepareAsync() // 异步准备，完成后会调用 onPrepared
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        // 当 MediaPlayer 准备完成时自动开始播放
        mp?.start()
        isPlaying.postValue(true)
        startUpdatingPlaybackPosition()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun startUpdatingPlaybackPosition() {
        stopUpdatingPlaybackPosition()
        playbackPositionTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            playbackPosition.postValue(it.currentPosition)
                        }
                    }
                }
            }, 0, 1000) // 每秒更新一次
        }
    }

    private fun stopUpdatingPlaybackPosition() {
        playbackPositionTimer?.cancel()
        playbackPositionTimer = null
    }

    fun pauseSong() {
        mediaPlayer?.pause()
        isPlaying.postValue(false)
        // 更新通知但不停止前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun resumeSong() {
        mediaPlayer?.start()
        isPlaying.postValue(true)
        startUpdatingPlaybackPosition()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun stopSong() {
        mediaPlayer?.stop()
        isPlaying.postValue(false)
        stopUpdatingPlaybackPosition()
        stopForeground(true)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        when (repeatMode) {
            RepeatMode.ONE -> {
                mp?.seekTo(0)
                mp?.start()
            }
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                if (currentSongIndex < currentPlaylist.size - 1) {
                    playNext()
                } else {
                    // 播放列表结束
                    isPlaying.postValue(false)
                    stopForeground(true)
                }
            }
        }
    }

    fun playNext() {
        if (currentPlaylist.isEmpty()) return

        currentSongIndex = if (isShuffle) {
            (0 until currentPlaylist.size).random()
        } else {
            when (repeatMode) {
                RepeatMode.ALL -> (currentSongIndex + 1) % currentPlaylist.size
                else -> (currentSongIndex + 1).coerceAtMost(currentPlaylist.size - 1)
            }
        }
        playSong(currentPlaylist[currentSongIndex])
    }

    fun playPrevious() {
        if (currentPlaylist.isEmpty()) return

        currentSongIndex = if (isShuffle) {
            (0 until currentPlaylist.size).random()
        } else {
            when (repeatMode) {
                RepeatMode.ALL -> if (currentSongIndex - 1 < 0) currentPlaylist.size - 1 else currentSongIndex - 1
                else -> (currentSongIndex - 1).coerceAtLeast(0)
            }
        }
        playSong(currentPlaylist[currentSongIndex])
    }

    fun toggleShuffle(): Boolean {
        isShuffle = !isShuffle
        isShuffleLiveData.postValue(isShuffle)
        return isShuffle
    }

    fun toggleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatModeLiveData.postValue(repeatMode)
        return repeatMode
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        playbackPosition.postValue(position)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music Player Controls"
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val song = currentSong.value
        val isCurrentlyPlaying = isPlaying.value ?: false

        // 创建 PendingIntent 用于打开播放器（优先打开 PlayerActivity）
        val openPlayerIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPlayerPendingIntent = PendingIntent.getActivity(
            this, 0, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建播放控制的 PendingIntent
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = if (isCurrentlyPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 3, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "未知歌曲")
            .setContentText(song?.artist ?: "未知艺术家")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openPlayerPendingIntent)
            .addAction(
                R.drawable.ic_skip_previous,
                "Previous",
                previousPendingIntent
            )
            .addAction(
                if (isCurrentlyPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isCurrentlyPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_skip_next,
                "Next",
                nextPendingIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .build()
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        stopUpdatingPlaybackPosition()
    }
}