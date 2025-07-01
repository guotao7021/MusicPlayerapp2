package com.huanmie.musicplayerapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        private val ivActionIcon: ImageView = itemView.findViewById(R.id.iv_add_song) // ID is generic, but it's the action icon

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
                ivActionIcon.visibility = View.VISIBLE

                // 根据功能改变图标和点击行为
                if (showRemoveOption && onRemoveFromPlaylistClick != null) {
                    // **FIX**: 在播放列表详情中，显示删除图标并直接触发删除操作
                    // 这避免了使用有样式问题的PopupMenu
                    ivActionIcon.setImageResource(R.drawable.ic_delete) // 使用删除图标
                    ivActionIcon.contentDescription = "从播放列表移除"
                    ivActionIcon.setOnClickListener {
                        // 直接调用在Fragment中定义的删除方法（该方法会显示确认对话框）
                        onRemoveFromPlaylistClick.invoke(song)
                    }
                    // 移除长按监听器，因为不再需要上下文菜单
                    itemView.setOnLongClickListener(null)
                } else if (onAddToPlaylistClick != null) {
                    // 在所有歌曲列表页，显示“添加”图标
                    ivActionIcon.setImageResource(R.drawable.ic_add)
                    ivActionIcon.contentDescription = "添加到播放列表"
                    ivActionIcon.setOnClickListener {
                        onAddToPlaylistClick.invoke(song)
                    }
                }
            } else {
                ivActionIcon.visibility = View.GONE
            }
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
