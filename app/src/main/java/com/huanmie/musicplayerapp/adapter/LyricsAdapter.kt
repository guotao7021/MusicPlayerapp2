package com.huanmie.musicplayerapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.lyrics.LyricLine

class LyricsAdapter : ListAdapter<LyricLine, LyricsAdapter.LyricsViewHolder>(LyricsDiffCallback()) {

    private var highlightIndex = -1
    private var onLyricClickListener: ((Int) -> Unit)? = null

    fun setHighlight(index: Int) {
        if (index != highlightIndex) {
            val oldIndex = highlightIndex
            highlightIndex = index

            // 只更新变化的项目
            if (oldIndex >= 0 && oldIndex < itemCount) {
                notifyItemChanged(oldIndex)
            }
            if (index >= 0 && index < itemCount) {
                notifyItemChanged(index)
            }
        }
    }

    fun setOnLyricClickListener(listener: (Int) -> Unit) {
        onLyricClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return LyricsViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricsViewHolder, position: Int) {
        val lyricLine = getItem(position)
        val isHighlighted = position == highlightIndex
        holder.bind(lyricLine, isHighlighted, position, onLyricClickListener)
    }

    class LyricsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvLyricLine)

        fun bind(
            lyricLine: LyricLine,
            isHighlighted: Boolean,
            position: Int,
            onClickListener: ((Int) -> Unit)?
        ) {
            textView.text = lyricLine.text

            // 设置高亮状态
            if (isHighlighted) {
                textView.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.button_active)
                )
                textView.textSize = 16f
                textView.alpha = 1.0f
            } else {
                textView.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_light_gray)
                )
                textView.textSize = 14f
                textView.alpha = 0.7f
            }

            // 设置点击监听器
            itemView.setOnClickListener {
                onClickListener?.invoke(position)
            }
        }
    }

    class LyricsDiffCallback : DiffUtil.ItemCallback<LyricLine>() {
        override fun areItemsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean {
            return oldItem.timeMs == newItem.timeMs
        }

        override fun areContentsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean {
            return oldItem == newItem
        }
    }
}