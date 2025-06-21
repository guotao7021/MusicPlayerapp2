package com.example.musicplayerapp2.lyrics

import android.content.Context
import android.os.Environment
import com.example.musicplayerapp2.data.Song
import java.io.File
import java.util.regex.Pattern

/**
 * 歌词行数据类
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * 歌词管理器 - 负责歌词文件的查找、解析和管理
 */
class LyricsManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: LyricsManager? = null

        fun getInstance(): LyricsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsManager().also { INSTANCE = it }
            }
        }
    }

    // LRC时间格式正则表达式
    private val lrcTimePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)") // [00:12.34]歌词
    private val lrcTimePattern2 = Pattern.compile("\\[(\\d{2}):(\\d{2}):(\\d{2})\\](.*)") // [00:12:34]歌词

    /**
     * 获取歌曲的歌词
     */
    fun getLyrics(context: Context, song: Song): List<LyricLine> {
        return try {
            // 尝试多种方式获取歌词
            findLyricsFile(song)?.let { lyricsFile ->
                parseLrcFile(lyricsFile)
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 查找歌词文件
     */
    private fun findLyricsFile(song: Song): File? {
        // 获取音乐文件的目录
        val musicFile = File(song.data)
        val musicDir = musicFile.parentFile ?: return null

        // 获取不带扩展名的文件名
        val nameWithoutExtension = musicFile.nameWithoutExtension

        // 可能的歌词文件名
        val possibleNames = listOf(
            "$nameWithoutExtension.lrc",
            "$nameWithoutExtension.txt",
            "${song.title}.lrc",
            "${song.title}.txt",
            "${song.artist} - ${song.title}.lrc",
            "${song.artist} - ${song.title}.txt"
        )

        // 在同一目录下查找歌词文件
        for (name in possibleNames) {
            val lyricsFile = File(musicDir, name)
            if (lyricsFile.exists() && lyricsFile.canRead()) {
                return lyricsFile
            }
        }

        // 在常见的歌词目录中查找
        val commonLyricsDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Lyrics"),
            File(Environment.getExternalStorageDirectory(), "Music/Lyrics"),
            File(musicDir, "Lyrics")
        )

        for (dir in commonLyricsDirs) {
            if (dir.exists() && dir.isDirectory) {
                for (name in possibleNames) {
                    val lyricsFile = File(dir, name)
                    if (lyricsFile.exists() && lyricsFile.canRead()) {
                        return lyricsFile
                    }
                }
            }
        }

        return null
    }

    /**
     * 解析LRC歌词文件
     */
    private fun parseLrcFile(file: File): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()

        try {
            file.readLines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty()) {
                    parseLrcLine(trimmedLine)?.let { lyricLine ->
                        lyrics.add(lyricLine)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 按时间排序
        return lyrics.sortedBy { it.timeMs }
    }

    /**
     * 解析单行LRC歌词
     */
    private fun parseLrcLine(line: String): LyricLine? {
        // 尝试匹配 [mm:ss.xx] 格式
        val matcher1 = lrcTimePattern.matcher(line)
        if (matcher1.find()) {
            val minutes = matcher1.group(1)?.toLongOrNull() ?: 0
            val seconds = matcher1.group(2)?.toLongOrNull() ?: 0
            val centiseconds = matcher1.group(3)?.toLongOrNull() ?: 0
            val text = matcher1.group(4)?.trim() ?: ""

            val timeMs = minutes * 60 * 1000 + seconds * 1000 + centiseconds * 10
            return LyricLine(timeMs, text)
        }

        // 尝试匹配 [mm:ss:xx] 格式
        val matcher2 = lrcTimePattern2.matcher(line)
        if (matcher2.find()) {
            val minutes = matcher2.group(1)?.toLongOrNull() ?: 0
            val seconds = matcher2.group(2)?.toLongOrNull() ?: 0
            val centiseconds = matcher2.group(3)?.toLongOrNull() ?: 0
            val text = matcher2.group(4)?.trim() ?: ""

            val timeMs = minutes * 60 * 1000 + seconds * 1000 + centiseconds * 10
            return LyricLine(timeMs, text)
        }

        return null
    }

    /**
     * 根据播放时间获取当前应该高亮的歌词索引
     */
    fun getCurrentLyricIndex(lyrics: List<LyricLine>, currentTimeMs: Long): Int {
        if (lyrics.isEmpty()) return -1

        // 找到最后一个时间小于等于当前时间的歌词
        var index = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= currentTimeMs) {
                index = i
            } else {
                break
            }
        }

        return index
    }

    /**
     * 创建示例歌词（用于测试）
     */
    fun createSampleLyrics(): List<LyricLine> {
        return listOf(
            LyricLine(0, "暂无歌词"),
            LyricLine(5000, "请在音乐文件同目录下"),
            LyricLine(10000, "放置对应的 .lrc 歌词文件"),
            LyricLine(15000, "文件名需要与音乐文件名相同"),
            LyricLine(20000, "例如: 歌曲.mp3 -> 歌曲.lrc"),
            LyricLine(25000, "点击任意歌词可跳转播放"),
            LyricLine(30000, "点击专辑封面切换歌词显示")
        )
    }
}