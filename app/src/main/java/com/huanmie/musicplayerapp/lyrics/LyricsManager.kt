package com.huanmie.musicplayerapp.lyrics

import android.content.Context
import android.os.Environment
import com.huanmie.musicplayerapp.data.Song
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
        @Volatile private var INSTANCE: LyricsManager? = null

        fun getInstance(): LyricsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsManager().also { INSTANCE = it }
            }
        }
    }

    // LRC 时间格式正则 (使用 Kotlin 原始字符串避免双重转义)
    private val lrcTimePattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2})\](.*)""")
    private val lrcTimePattern2 = Pattern.compile("""\[(\d{2}):(\d{2}):(\d{2})\](.*)""")

    // VTT 时间格式正则 (使用 Kotlin 原始字符串)
    private val vttTimePattern = Pattern.compile(
        """
        (?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*
        (?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})
        """.trimIndent()
    )

    /**
     * 获取歌词，自动根据后缀分发到对应解析方法
     */
    fun getLyrics(context: Context, song: Song): List<LyricLine> {
        return try {
            findLyricsFile(song)?.let { file ->
                when (file.extension.lowercase()) {
                    "lrc" -> parseLrcFile(file)
                    "vtt" -> parseVttFile(file)
                    else   -> emptyList()
                }
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 查找歌词文件，.lrc/.vtt 部分匹配（同目录 → 常见目录 → 全机扫描）
     */
    private fun findLyricsFile(song: Song): File? {
        val musicFile = File(song.data)
        val musicDir  = musicFile.parentFile ?: return null

        val titleLower  = song.title.lowercase().trim()
        val artistLower = song.artist.lowercase().trim()
        val exts = listOf("lrc", "vtt")

        // 1. 同目录扫描
        musicDir.listFiles { f -> f.isFile && exts.contains(f.extension.lowercase()) }
            ?.forEach { f ->
                val nameLower = f.nameWithoutExtension.lowercase()
                if (nameLower.contains(titleLower) || nameLower.contains(artistLower)) {
                    return f
                }
            }

        // 2. 常见目录扫描
        listOf(
            File(Environment.getExternalStorageDirectory(), "Lyrics"),
            File(Environment.getExternalStorageDirectory(), "Music/Lyrics"),
            File(musicDir, "Lyrics")
        ).filter { it.exists() && it.isDirectory }
            .forEach { dir ->
                dir.listFiles { f -> f.isFile && exts.contains(f.extension.lowercase()) }
                    ?.forEach { f ->
                        val nameLower = f.nameWithoutExtension.lowercase()
                        if (nameLower.contains(titleLower) || nameLower.contains(artistLower)) {
                            return f
                        }
                    }
            }

        // 3. 全机扫描
        Environment.getExternalStorageDirectory().walkTopDown()
            .filter { it.isFile && exts.contains(it.extension.lowercase()) }
            .forEach { f ->
                val nameLower = f.nameWithoutExtension.lowercase()
                if (nameLower.contains(titleLower) || nameLower.contains(artistLower)) {
                    return f
                }
            }

        return null
    }

    /**
     * 解析 .lrc 文件
     */
    private fun parseLrcFile(file: File): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        file.readLines().forEach { line ->
            parseLrcLine(line.trim())?.let { lyrics.add(it) }
        }
        return lyrics.sortedBy { it.timeMs }
    }

    private fun parseLrcLine(line: String): LyricLine? {
        lrcTimePattern.matcher(line).takeIf { it.find() }?.let { m ->
            val mMin = m.group(1)?.toLongOrNull() ?: 0L
            val mSec = m.group(2)?.toLongOrNull() ?: 0L
            val cSec = m.group(3)?.toLongOrNull() ?: 0L
            val txt  = m.group(4)?.trim() ?: ""
            return LyricLine(mMin * 60_000 + mSec * 1_000 + cSec * 10, txt)
        }
        lrcTimePattern2.matcher(line).takeIf { it.find() }?.let { m ->
            val h    = m.group(1)?.toLongOrNull() ?: 0L
            val min  = m.group(2)?.toLongOrNull() ?: 0L
            val sec  = m.group(3)?.toLongOrNull() ?: 0L
            val txt2 = m.group(4)?.trim() ?: ""
            return LyricLine(h * 3_600_000 + min * 60_000 + sec * 1_000, txt2)
        }
        return null
    }

    /**
     * 解析 .vtt 文件
     */
    private fun parseVttFile(file: File): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        val lines = file.readLines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val m = vttTimePattern.matcher(line)
            if (m.find()) {
                // 安全调用一行完成，避免换行错误
                val h1 = m.group(1)?.toLongOrNull() ?: 0L
                val m1 = m.group(2)?.toLongOrNull() ?: 0L
                val s1 = m.group(3)?.toLongOrNull() ?: 0L
                val ms = m.group(4)?.toLongOrNull() ?: 0L
                val startTime = h1 * 3_600_000 + m1 * 60_000 + s1 * 1_000 + ms

                val sb = StringBuilder()
                var j = i + 1
                while (j < lines.size && lines[j].trim().isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(lines[j].trim())
                    j++
                }
                lyrics.add(LyricLine(startTime, sb.toString()))
                i = j
            } else {
                i++
            }
        }
        return lyrics.sortedBy { it.timeMs }
    }

    /**
     * 获取当前高亮索引
     */
    fun getCurrentLyricIndex(lyrics: List<LyricLine>, currentTimeMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs > currentTimeMs) return i - 1
        }
        return lyrics.size - 1
    }

    /**
     * 创建示例歌词（用于缺省显示）
     */
    fun createSampleLyrics(): List<LyricLine> {
        return listOf(
            LyricLine(0, "暂无歌词"),
            LyricLine(5_000, "请在音乐文件同目录下"),
            LyricLine(10_000, "放置对应的 .lrc 或 .vtt 歌词文件"),
            LyricLine(15_000, "文件名需包含歌曲名或歌手名"),
            LyricLine(20_000, "例如: 歌曲.mp3 -> 歌曲.lrc"),
            LyricLine(25_000, "点击任意歌词可跳转播放"),
            LyricLine(30_000, "点击专辑封面切换歌词显示")
        )
    }
}
