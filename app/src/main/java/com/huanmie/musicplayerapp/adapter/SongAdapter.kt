package com.huanmie.musicplayerapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.data.Song

// SongAdapter 负责显示歌曲列表
class SongAdapter(
    private val onItemClick: (Song, Int) -> Unit,
    private val onAddToPlaylistClick: ((Song) -> Unit)? = null,
    private val onRemoveFromPlaylistClick: ((Song) -> Unit)? = null,
    private val showRemoveOption: Boolean = false // 是否显示从播放列表删除选项
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    // SongViewHolder 用于持有歌曲列表项的视图
    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSongTitle: TextView = itemView.findViewById(R.id.tv_song_title)
        private val tvSongArtistSource: TextView = itemView.findViewById(R.id.tv_song_artist_source)
        private val tvSongDuration: TextView = itemView.findViewById(R.id.tv_song_duration)
        private val ivAddSong: ImageView = itemView.findViewById(R.id.iv_add_song)

        // 绑定歌曲数据到视图
        fun bind(
            song: Song,
            position: Int,
            onItemClick: (Song, Int) -> Unit,
            onAddToPlaylistClick: ((Song) -> Unit)?,
            onRemoveFromPlaylistClick: ((Song) -> Unit)?,
            showRemoveOption: Boolean
        ) {
            tvSongTitle.text = song.title
            tvSongArtistSource.text = song.artist

            // 格式化歌曲时长
            val minutes = (song.duration / 1000) / 60
            val seconds = (song.duration / 1000) % 60
            tvSongDuration.text = String.format("%d:%02d", minutes, seconds)

            // 设置点击监听器
            itemView.setOnClickListener {
                onItemClick(song, position)
            }

            // 设置右侧按钮的功能
            if (onAddToPlaylistClick != null || onRemoveFromPlaylistClick != null) {
                ivAddSong.visibility = View.VISIBLE

                // 根据功能改变图标和点击行为
                if (showRemoveOption && onRemoveFromPlaylistClick != null) {
                    // 在播放列表详情页显示更多选项
                    ivAddSong.setImageResource(R.drawable.ic_more_vert)
                    ivAddSong.contentDescription = "更多选项"
                    ivAddSong.setOnClickListener {
                        showContextMenu(song, ivAddSong, onAddToPlaylistClick, onRemoveFromPlaylistClick)
                    }

                    // 长按也显示菜单
                    itemView.setOnLongClickListener {
                        showContextMenu(song, ivAddSong, onAddToPlaylistClick, onRemoveFromPlaylistClick)
                        true
                    }
                } else if (onAddToPlaylistClick != null) {
                    // 在歌曲列表页显示添加按钮
                    ivAddSong.setImageResource(R.drawable.ic_add)
                    ivAddSong.contentDescription = "添加到播放列表"
                    ivAddSong.setOnClickListener {
                        onAddToPlaylistClick.invoke(song)
                    }
                }
            } else {
                ivAddSong.visibility = View.GONE
            }
        }

        private fun showContextMenu(
            song: Song,
            anchor: ImageView,
            onAddToPlaylistClick: ((Song) -> Unit)?,
            onRemoveFromPlaylistClick: ((Song) -> Unit)?
        ) {
            // 修复：直接使用普通的PopupMenu，不应用主题包装器
            val popup = PopupMenu(anchor.context, anchor)

            // 根据情况添加菜单项
            if (onAddToPlaylistClick != null) {
                popup.menu.add(0, 1, 0, "添加到播放列表")
            }

            if (onRemoveFromPlaylistClick != null) {
                popup.menu.add(0, 2, 0, "从播放列表移除")
            }

            // 强制设置图标显示（可选）
            try {
                val field = popup.javaClass.getDeclaredField("mPopup")
                field.isAccessible = true
                val menuPopupHelper = field.get(popup)
                val method = menuPopupHelper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                method.isAccessible = true
                method.invoke(menuPopupHelper, true)
            } catch (e: Exception) {
                // 忽略反射失败
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onAddToPlaylistClick?.invoke(song)
                        true
                    }
                    2 -> {
                        onRemoveFromPlaylistClick?.invoke(song)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
    }

    // 创建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    // 绑定数据到 ViewHolder
    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, position, onItemClick, onAddToPlaylistClick, onRemoveFromPlaylistClick, showRemoveOption)
    }

    // SongDiffCallback 用于高效地更新 RecyclerView
    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}