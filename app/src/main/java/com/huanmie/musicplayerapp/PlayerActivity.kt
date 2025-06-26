package com.huanmie.musicplayerapp

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Rational
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import com.huanmie.musicplayerapp.adapter.LyricsAdapter
import com.huanmie.musicplayerapp.data.Song
import com.huanmie.musicplayerapp.databinding.ActivityPlayerBinding
import com.huanmie.musicplayerapp.databinding.LayoutPipControlsBinding
import com.huanmie.musicplayerapp.lyrics.LyricLine
import com.huanmie.musicplayerapp.lyrics.LyricsManager
import com.huanmie.musicplayerapp.service.MusicService
import com.huanmie.musicplayerapp.service.MusicService.RepeatMode
import com.huanmie.musicplayerapp.viewmodel.PlayerViewModel
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var pipControlsBinding: LayoutPipControlsBinding
    private val playerViewModel: PlayerViewModel by viewModels()
    private var musicService: MusicService? = null
    private var isFavorite = false
    private var isLyricsVisible = true
    private var currentLyrics: List<LyricLine> = emptyList()

    // 歌词相关
    private lateinit var lyricsAdapter: LyricsAdapter
    private lateinit var lyricsManager: LyricsManager

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()

            // 播放列表
            val playlist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, Song::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Song>(MusicService.EXTRA_SONG_LIST)
            }
            val index = intent.getIntExtra(MusicService.EXTRA_SONG_INDEX, 0)
            playlist?.let { musicService?.setPlaylistAndPlay(it, index) }

            // 观察 Service LiveData
            setupObservers()

            // 观察 shuffle/repeat
            musicService?.isShuffleLiveData?.observe(this@PlayerActivity, Observer { updateShuffleButtonUI(it) })
            musicService?.repeatModeLiveData?.observe(this@PlayerActivity, Observer { updateRepeatButtonUI(it) })

            // 注册按钮
            setupControls()

            // 初始化 UI 状态
            musicService?.let {
                updateShuffleButtonUI(it.isShuffle)
                updateRepeatButtonUI(it.repeatMode)
                updateFavoriteButtonUI(isFavorite)
                updatePlayPauseButtonUI(it.isPlaying.value == true)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
            ) {
                enterPipMode()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        // 初始化歌词视图
        setupLyrics()
        // 初始化 PiP 控件
        setupPipControls()

        // 绑定服务
        Intent(this, MusicService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupLyrics() {
        lyricsManager = LyricsManager.getInstance()
        lyricsAdapter = LyricsAdapter()
        binding.rvLyrics.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = lyricsAdapter
            isNestedScrollingEnabled = false
            visibility = View.VISIBLE
        }
        binding.rvLyrics.setOnClickListener { toggleLyricsVisibility() }
        lyricsAdapter.setOnLyricClickListener { pos ->
            currentLyrics.getOrNull(pos)?.let { musicService?.seekTo(it.timeMs.toInt()) }
        }
    }

    private fun setupPipControls() {
        pipControlsBinding = LayoutPipControlsBinding.inflate(layoutInflater)
        pipControlsBinding.btnPipPlayPause.setOnClickListener {
            musicService?.isPlaying?.value?.let {
                if (it) musicService?.pauseSong() else musicService?.resumeSong()
            }
        }
        pipControlsBinding.btnPipNext.setOnClickListener { musicService?.playNext() }
        pipControlsBinding.btnPipPrevious.setOnClickListener { musicService?.playPrevious() }
        pipControlsBinding.layoutSongInfo.setOnClickListener {
            if (isInPictureInPictureMode)
                startActivity(
                    Intent(this, PlayerActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
                )
        }
    }

    private fun setupObservers() {
        musicService?.currentSong?.observe(this, Observer<Song?> { song ->
            song?.let {
                updateUI(it)
                loadLyrics(it)
                if (isInPictureInPictureMode) updatePipUI()
            }
        })
        musicService?.isPlaying?.observe(this, Observer<Boolean> { playing ->
            updatePlayPauseButtonUI(playing)
        })
        musicService?.playbackPosition?.observe(this, Observer<Int> { pos ->
            binding.seekBar.progress = pos
            binding.tvCurrentTime.text = formatTime(pos.toLong())
            updateLyricsHighlight(pos.toLong())
        })
    }

    private fun loadLyrics(song: Song) {
        Thread {
            val lyrics = lyricsManager.getLyrics(this, song).ifEmpty { lyricsManager.createSampleLyrics() }
            currentLyrics = lyrics
            runOnUiThread { lyricsAdapter.submitList(currentLyrics) }
        }.start()
    }

    private fun updateLyricsHighlight(timeMs: Long) {
        if (!isLyricsVisible) return
        val idx = lyricsManager.getCurrentLyricIndex(currentLyrics, timeMs)
        lyricsAdapter.setHighlight(idx)
        scrollToLyricCenter(idx)
    }

    private fun scrollToLyricCenter(index: Int) {
        (binding.rvLyrics.layoutManager as? LinearLayoutManager)?.let { lm ->
            val visibleCount = lm.findLastVisibleItemPosition() - lm.findFirstVisibleItemPosition() + 1
            val offset = visibleCount / 2
            val target = (index - offset).coerceAtLeast(0)
            LinearSmoothScroller(this).apply { targetPosition = target; lm.startSmoothScroll(this) }
        }
    }

    private fun toggleLyricsVisibility() {
        isLyricsVisible = !isLyricsVisible
        binding.rvLyrics.visibility = if (isLyricsVisible) View.VISIBLE else View.INVISIBLE
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.isPlaying?.value?.let { if (it) musicService?.pauseSong() else musicService?.resumeSong() }
        }
        binding.btnNext.setOnClickListener { musicService?.playNext() }
        binding.btnPrevious.setOnClickListener { musicService?.playPrevious() }
        binding.btnShuffle.setOnClickListener { musicService?.toggleShuffle()?.let { updateShuffleButtonUI(it) } }
        binding.btnRepeat.setOnClickListener { musicService?.toggleRepeatMode()?.let { updateRepeatButtonUI(it) } }
        binding.btnFavorite.setOnClickListener { isFavorite = !isFavorite; updateFavoriteButtonUI(isFavorite) }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateUI(song: Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist
        binding.tvTotalTime.text = formatTime(song.duration)
        binding.seekBar.max = song.duration.toInt()
        loadAlbumArt(song.data)
    }

    private fun loadAlbumArt(path: String) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                binding.ivAlbumArt.setImageBitmap(bmp)
            } else {
                binding.ivAlbumArt.setImageResource(R.drawable.ic_default_cover)
            }
        } catch (_: Exception) {
            binding.ivAlbumArt.setImageResource(R.drawable.ic_default_cover)
        } finally {
            retriever.release()
        }
    }

    private fun updatePipUI() {
        musicService?.currentSong?.value?.let { song ->
            pipControlsBinding.tvPipSongTitle.text = song.title
            pipControlsBinding.tvPipArtist.text = song.artist
            loadAlbumArtForPip(song.data)
        }
        musicService?.isPlaying?.value?.let { updatePipPlayPauseButton(it) }
    }

    private fun loadAlbumArtForPip(path: String) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                pipControlsBinding.ivPipAlbumArt.setImageBitmap(bmp)
            } else {
                pipControlsBinding.ivPipAlbumArt.setImageResource(R.drawable.ic_default_cover)
            }
        } catch (_: Exception) {
            pipControlsBinding.ivPipAlbumArt.setImageResource(R.drawable.ic_default_cover)
        } finally {
            retriever.release()
        }
    }

    private fun updateRepeatButtonUI(mode: RepeatMode) {
        val iconRes = when (mode) {
            RepeatMode.ONE -> R.drawable.ic_repeat_one
            RepeatMode.ALL -> R.drawable.ic_repeat_all
            RepeatMode.OFF -> R.drawable.ic_repeat_all
        }
        binding.btnRepeat.setImageResource(iconRes)
        val color = if (mode == RepeatMode.OFF) getColor(R.color.text_light_gray) else getColor(R.color.button_active)
        binding.btnRepeat.imageTintList = ColorStateList.valueOf(color)
    }

    private fun updateShuffleButtonUI(on: Boolean) {
        val color = if (on) getColor(R.color.button_active) else getColor(R.color.text_light_gray)
        binding.btnShuffle.imageTintList = ColorStateList.valueOf(color)
    }

    private fun updateFavoriteButtonUI(fav: Boolean) {
        val iconRes = if (fav) R.drawable.ic_favorite_full else R.drawable.ic_favorite_border
        binding.btnFavorite.setImageResource(iconRes)
        val color = if (fav) getColor(R.color.button_active) else getColor(R.color.text_light_gray)
        binding.btnFavorite.imageTintList = ColorStateList.valueOf(color)
    }

    private fun updatePlayPauseButtonUI(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updatePipPlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        pipControlsBinding.btnPipPlayPause.setImageResource(icon)
    }

    private fun formatTime(ms: Long): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(ms)
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(m)
        return String.format("%02d:%02d", m, s)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(binding.root.width, binding.root.height))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            enterPipMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}