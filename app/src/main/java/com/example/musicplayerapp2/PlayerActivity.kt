package com.example.musicplayerapp2

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import com.example.musicplayerapp2.adapter.LyricsAdapter
import com.example.musicplayerapp2.data.Song
import com.example.musicplayerapp2.databinding.ActivityPlayerBinding
import com.example.musicplayerapp2.databinding.LayoutPipControlsBinding
import com.example.musicplayerapp2.lyrics.LyricLine
import com.example.musicplayerapp2.lyrics.LyricsManager
import com.example.musicplayerapp2.service.MusicService
import com.example.musicplayerapp2.viewmodel.PlayerViewModel
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var pipControlsBinding: LayoutPipControlsBinding
    private val playerViewModel: PlayerViewModel by viewModels()
    private var musicService: MusicService? = null
    private var isInPictureInPictureMode = false

    // 歌词相关
    private lateinit var lyricsAdapter: LyricsAdapter
    private lateinit var lyricsManager: LyricsManager
    private var currentLyrics: List<LyricLine> = emptyList()
    private var isLyricsVisible = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()

            // 从 Intent 中获取播放列表和索引
            val playlist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, Song::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Song>(MusicService.EXTRA_SONG_LIST)
            }
            val songIndex = intent.getIntExtra(MusicService.EXTRA_SONG_INDEX, 0)

            // 如果有播放列表数据，设置给服务
            playlist?.let { list ->
                musicService?.setPlaylistAndPlay(list, songIndex)
            }

            setupObservers()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    private fun loadLyrics(song: Song) {
        if (!isInPictureInPictureMode) {
            Thread {
                val lyrics = lyricsManager.getLyrics(this, song)
                currentLyrics = if (lyrics.isNotEmpty()) {
                    lyrics
                } else {
                    lyricsManager.createSampleLyrics()
                }

                runOnUiThread {
                    lyricsAdapter.submitList(currentLyrics)
                }
            }.start()
        }
    }

    private fun updateLyricsHighlight(currentTimeMs: Long) {
        if (currentLyrics.isNotEmpty() && isLyricsVisible && !isInPictureInPictureMode) {
            val currentIndex = lyricsManager.getCurrentLyricIndex(currentLyrics, currentTimeMs)
            lyricsAdapter.setHighlight(currentIndex)

            if (currentIndex >= 0) {
                scrollToLyricCenter(currentIndex)
            }
        }
    }

    private fun scrollToLyricCenter(index: Int) {
        val layoutManager = binding.rvLyrics.layoutManager as? LinearLayoutManager
        layoutManager?.let {
            val visibleItemCount = it.findLastVisibleItemPosition() - it.findFirstVisibleItemPosition() + 1
            val centerOffset = visibleItemCount / 2
            val targetPosition = (index - centerOffset).coerceAtLeast(0)

            val smoothScroller = object : LinearSmoothScroller(this) {
                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            }
            smoothScroller.targetPosition = targetPosition
            it.startSmoothScroll(smoothScroller)
        }
    }

    private fun toggleLyricsVisibility() {
        isLyricsVisible = !isLyricsVisible

        if (isLyricsVisible) {
            binding.rvLyrics.visibility = View.VISIBLE
            binding.rvLyrics.alpha = 0f
            binding.rvLyrics.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            binding.rvLyrics.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.rvLyrics.visibility = View.INVISIBLE
                }
                .start()
        }
    }

    private fun setupLyrics() {
        lyricsManager = LyricsManager.getInstance()
        lyricsAdapter = LyricsAdapter()

        binding.rvLyrics.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity).apply {
                stackFromEnd = false
            }
            adapter = lyricsAdapter
            visibility = View.VISIBLE
            isNestedScrollingEnabled = false
        }

        // 歌词点击跳转
        lyricsAdapter.setOnLyricClickListener { position ->
            if (position < currentLyrics.size) {
                val targetTime = currentLyrics[position].timeMs.toInt()
                musicService?.seekTo(targetTime)
            }
        }

        // 点击歌词区域切换显示/隐藏
        binding.rvLyrics.setOnClickListener {
            toggleLyricsVisibility()
        }

        // 长按歌词区域可以切换显示模式
        binding.rvLyrics.setOnLongClickListener {
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 PiP 控件布局
        pipControlsBinding = LayoutPipControlsBinding.inflate(layoutInflater)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            // 检查是否支持 PiP 模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                enterPipMode()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setupLyrics()
        setupControls()
        setupPipControls()

        // 启动并绑定音乐服务
        val serviceIntent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun setupControls() {
        // ▶️ 播放 / 暂停
        binding.btnPlayPause.setOnClickListener {
            musicService?.isPlaying?.value?.let { isPlaying ->
                if (isPlaying) {
                    musicService?.pauseSong()
                } else {
                    musicService?.resumeSong()
                }
            }
        }
        // ⏭️ 下一首
        binding.btnNext.setOnClickListener { musicService?.playNext() }
        // ⏮️ 上一首
        binding.btnPrevious.setOnClickListener { musicService?.playPrevious() }
        // 🔀 随机切换
        binding.btnShuffle.setOnClickListener { musicService?.toggleShuffle() }
        // 🔁 循环切换
        binding.btnRepeat.setOnClickListener { musicService?.toggleRepeatMode() }
        // 📻 拖动进度条
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupPipControls() {
        // 设置 PiP 模式下的控件点击事件
        pipControlsBinding.btnPipPlayPause.setOnClickListener {
            musicService?.isPlaying?.value?.let { isPlaying ->
                if (isPlaying) {
                    musicService?.pauseSong()
                } else {
                    musicService?.resumeSong()
                }
            }
        }

        pipControlsBinding.btnPipNext.setOnClickListener {
            musicService?.playNext()
        }

        pipControlsBinding.btnPipPrevious.setOnClickListener {
            musicService?.playPrevious()
        }

        // 点击歌曲信息区域返回全屏模式
        pipControlsBinding.layoutSongInfo.setOnClickListener {
            if (isInPictureInPictureMode) {
                // 创建 Intent 重新打开全屏播放器
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)) // 设置宽高比
                    .build()

                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果进入 PiP 失败，则正常返回
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        this.isInPictureInPictureMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            // 进入 PiP 模式：隐藏常规 UI，显示 PiP 控件
            binding.root.visibility = View.GONE
            setContentView(pipControlsBinding.root)

            // 更新 PiP 界面的歌曲信息
            updatePipUI()
        } else {
            // 退出 PiP 模式：恢复常规 UI
            setContentView(binding.root)
            binding.root.visibility = View.VISIBLE

            // 重新设置工具栏
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.toolbar.setNavigationOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    enterPipMode()
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    private fun updatePipUI() {
        musicService?.currentSong?.value?.let { song ->
            pipControlsBinding.tvPipSongTitle.text = song.title
            pipControlsBinding.tvPipArtist.text = song.artist

            // 加载专辑封面到 PiP 界面
            loadAlbumArtForPip(song.data)
        }

        // 更新播放按钮状态
        musicService?.isPlaying?.value?.let { isPlaying ->
            updatePipPlayPauseButton(isPlaying)
        }
    }

    private fun updatePipPlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }
        pipControlsBinding.btnPipPlayPause.setImageResource(iconRes)
    }

    private fun loadAlbumArtForPip(path: String) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                pipControlsBinding.ivPipAlbumArt.setImageBitmap(bitmap)
            } else {
                pipControlsBinding.ivPipAlbumArt.setImageResource(R.drawable.ic_default_cover)
            }
        } catch (e: Exception) {
            pipControlsBinding.ivPipAlbumArt.setImageResource(R.drawable.ic_default_cover)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun setupObservers() {
        musicService?.currentSong?.observe(this) { song ->
            song?.let {
                updateUI(it)
                loadLyrics(it)
                if (isInPictureInPictureMode) {
                    updatePipUI()
                }
            }
        }
        musicService?.isPlaying?.observe(this) { isPlaying ->
            updatePlayPauseButton(isPlaying)
            if (isInPictureInPictureMode) {
                updatePipPlayPauseButton(isPlaying)
            }
        }
        musicService?.playbackPosition?.observe(this) { pos ->
            if (!isInPictureInPictureMode) {
                binding.seekBar.progress = pos
                binding.tvCurrentTime.text = formatTime(pos.toLong())
                // 更新歌词高亮
                updateLyricsHighlight(pos.toLong())
            }
        }
    }

    private fun updateUI(song: Song) {
        if (!isInPictureInPictureMode) {
            binding.tvSongTitle.text = song.title
            binding.tvArtistName.text = song.artist
            loadAlbumArt(song.data)
            binding.tvTotalTime.text = formatTime(song.duration)
            binding.seekBar.max = song.duration.toInt()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (!isInPictureInPictureMode) {
            val icon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
            binding.btnPlayPause.setImageResource(icon)
        }
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

    private fun formatTime(ms: Long): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(ms)
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) -
                TimeUnit.MINUTES.toSeconds(m)
        return String.format("%02d:%02d", m, s)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 当用户按 Home 键时自动进入 PiP 模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPipMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}