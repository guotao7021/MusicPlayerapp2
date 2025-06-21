package com.example.musicplayerapp2.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayerapp2.R
import com.example.musicplayerapp2.data.Playlist

class PlaylistAdapter(
    private val playlists: MutableList<Playlist>,
    private val onItemClick: (Playlist) -> Unit,
    private val onRenameClick: (Playlist) -> Unit = {},
    private val onDeleteClick: (Playlist) -> Unit = {}
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon: ImageView = itemView.findViewById(R.id.ivFolder)
        private val nameTextView: TextView = itemView.findViewById(R.id.tvPlaylistName)
        private val songCountTextView: TextView = itemView.findViewById(R.id.tv_song_count)
        private val moreButton: ImageView = itemView.findViewById(R.id.iv_playlist_more)

        fun bind(playlist: Playlist) {
            nameTextView.text = playlist.name
            songCountTextView.text = "${playlist.songs.size} 首歌曲"

            // 点击播放列表进入详情
            itemView.setOnClickListener { onItemClick(playlist) }

            // 长按显示菜单
            itemView.setOnLongClickListener {
                showContextMenu(playlist)
                true
            }

            // 更多按钮点击
            moreButton.setOnClickListener {
                showContextMenu(playlist)
            }
        }

        private fun showContextMenu(playlist: Playlist) {
            val popup = PopupMenu(itemView.context, moreButton)
            popup.menuInflater.inflate(R.menu.playlist_context_menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_rename_playlist -> {
                        onRenameClick(playlist)
                        true
                    }
                    R.id.menu_delete_playlist -> {
                        onDeleteClick(playlist)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_enhanced, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount(): Int = playlists.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newPlaylists: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        if (position >= 0 && position < playlists.size) {
            playlists.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateItem(position: Int, playlist: Playlist) {
        if (position >= 0 && position < playlists.size) {
            playlists[position] = playlist
            notifyItemChanged(position)
        }
    }
}