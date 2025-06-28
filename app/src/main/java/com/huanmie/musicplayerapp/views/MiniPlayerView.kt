package com.huanmie.musicplayerapp.views

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.huanmie.musicplayerapp.PlayerActivity
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.data.Song
import com.huanmie.musicplayerapp.service.MusicService
import java.util.concurrent.TimeUnit

class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val ivMiniAlbumArt: ImageView
    private val tvMiniSongTitle: TextView
    private val tvMiniArtist: TextView
    private val btnMiniPlayPause: ImageButton
    private val btnMiniNext: ImageButton
    private val btnMiniClose: ImageButton

    private var musicService: MusicService? = null
    private var currentSong: Song? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_mini_player, this, true)

        ivMiniAlbumArt = findViewById(R.id.iv_mini_album_art)
        tvMiniSongTitle = findViewById(R.id.tv_mini_song_title)
        tvMiniArtist = findViewById(R.id.tv_mini_artist)
        btnMiniPlayPause = findViewById(R.id.btn_mini_play_pause)
        btnMiniNext = findViewById(R.id.btn_mini_next)
        btnMiniClose = findViewById(R.id.btn_mini_close)

        setupClickListeners()
        visibility = View.GONE
    }

    private fun setupClickListeners() {
        // 点击播放/暂停
        btnMiniPlayPause.setOnClickListener {
            musicService?.isPlaying?.value?.let { isPlaying ->
                if (isPlaying) {
                    musicService?.pauseSong()
                } else {
                    musicService?.resumeSong()
                }
            }
        }

        // 点击下一首
        btnMiniNext.setOnClickListener {
            musicService?.playNext()
        }

        // 点击关闭
        btnMiniClose.setOnClickListener {
            hide()
            musicService?.stopSong()
        }

        // 点击主体区域打开完整播放器
        findViewById<View>(R.id.mini_player_main).setOnClickListener {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    fun bindMusicService(service: MusicService, lifecycleOwner: LifecycleOwner) {
        musicService = service

        // 观察当前歌曲
        service.currentSong.observe(lifecycleOwner, Observer { song ->
            song?.let {
                currentSong = it
                updateSongInfo(it)
                // 只有在播放状态时才显示
                if (service.isPlaying.value == true) {
                    show()
                }
            }
        })

        // 观察播放状态
        service.isPlaying.observe(lifecycleOwner, Observer { isPlaying ->
            updatePlayPauseButton(isPlaying)
            // 如果停止播放且没有歌曲，隐藏播放器
            if (!isPlaying && currentSong == null) {
                hide()
            }
        })
    }

    private fun updateSongInfo(song: Song) {
        tvMiniSongTitle.text = song.title
        tvMiniArtist.text = song.artist

        // 这里可以加载专辑封面
        // 为了简单起见，使用默认图标
        ivMiniAlbumArt.setImageResource(R.drawable.ic_default_cover)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnMiniPlayPause.setImageResource(iconRes)
    }

    fun show() {
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    fun hide() {
        if (visibility == View.VISIBLE) {
            animate()
                .translationY(height.toFloat())
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }
    }

    fun shouldShow(): Boolean {
        return musicService?.currentSong?.value != null &&
                musicService?.isPlaying?.value == true
    }
}