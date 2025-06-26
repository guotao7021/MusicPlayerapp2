package com.huanmie.musicplayerapp.lyrics

import android.content.Context
import android.os.Environment
import android.util.Log
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
        private const val TAG = "LyricsManager"

        fun getInstance(): LyricsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsManager().also { INSTANCE = it }
            }
        }
    }

    // LRC 时间格式正则
    private val lrcTimePattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2})\](.*)""")
    private val lrcTimePattern2 = Pattern.compile("""\[(\d{2}):(\d{2}):(\d{2})\](.*)""")

    // VTT 时间格式正则 - 修复版本
    private val vttTimePattern = Pattern.compile(
        """(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(\d{2}):(\d{2})\.(\d{3})"""
    )

    // 支持带小时的VTT格式
    private val vttTimePatternWithHour = Pattern.compile(
        """(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})\.(\d{3})"""
    )

    /**
     * 获取歌词，自动根据后缀分发到对应解析方法
     */
    fun getLyrics(context: Context, song: Song): List<LyricLine> {
        return try {
            val lyricsFile = findLyricsFile(song)
            Log.d(TAG, "找到的歌词文件: ${lyricsFile?.absolutePath}")

            lyricsFile?.let { file ->
                when (file.extension.lowercase()) {
                    "lrc" -> {
                        Log.d(TAG, "解析LRC文件: ${file.name}")
                        parseLrcFile(file)
                    }
                    "vtt" -> {
                        Log.d(TAG, "解析VTT文件: ${file.name}")
                        parseVttFile(file)
                    }
                    else -> {
                        Log.w(TAG, "不支持的歌词文件格式: ${file.extension}")
                        emptyList()
                    }
                }
            } ?: run {
                Log.w(TAG, "未找到歌词文件: ${song.title} - ${song.artist}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析歌词时出错", e)
            emptyList()
        }
    }

    /**
     * 查找歌词文件 - 改进版本，增加调试信息
     */
    private fun findLyricsFile(song: Song): File? {
        val musicFile = File(song.data)
        val musicDir = musicFile.parentFile ?: return null

        val titleLower = song.title.lowercase().trim()
        val artistLower = song.artist.lowercase().trim()
        val musicFileName = musicFile.nameWithoutExtension.lowercase()
        val exts = listOf("lrc", "vtt")

        Log.d(TAG, "开始查找歌词文件 - 歌曲: $titleLower, 歌手: $artistLower")
        Log.d(TAG, "音乐文件名: $musicFileName")
        Log.d(TAG, "音乐目录: ${musicDir.absolutePath}")

        // 1. 同目录扫描 - 增强匹配逻辑
        musicDir.listFiles { f ->
            f.isFile && exts.contains(f.extension.lowercase())
        }?.forEach { f ->
            val nameLower = f.nameWithoutExtension.lowercase()
            Log.d(TAG, "检查同目录文件: ${f.name}")

            // 精确匹配音乐文件名
            if (nameLower == musicFileName) {
                Log.d(TAG, "找到精确匹配的歌词文件: ${f.name}")
                return f
            }
            // 包含歌曲名或歌手名
            if (nameLower.contains(titleLower) || nameLower.contains(artistLower)) {
                Log.d(TAG, "找到部分匹配的歌词文件: ${f.name}")
                return f
            }
        }

        // 2. 常见目录扫描
        val commonDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Lyrics"),
            File(Environment.getExternalStorageDirectory(), "Music/Lyrics"),
            File(musicDir, "Lyrics")
        )

        for (dir in commonDirs) {
            if (dir.exists() && dir.isDirectory) {
                Log.d(TAG, "扫描目录: ${dir.absolutePath}")
                dir.listFiles { f ->
                    f.isFile && exts.contains(f.extension.lowercase())
                }?.forEach { f ->
                    val nameLower = f.nameWithoutExtension.lowercase()
                    if (nameLower.contains(titleLower) || nameLower.contains(artistLower)) {
                        Log.d(TAG, "在常见目录中找到歌词文件: ${f.absolutePath}")
                        return f
                    }
                }
            }
        }

        Log.d(TAG, "未找到歌词文件")
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
        Log.d(TAG, "LRC解析完成，共${lyrics.size}行歌词")
        return lyrics.sortedBy { it.timeMs }
    }

    private fun parseLrcLine(line: String): LyricLine? {
        lrcTimePattern.matcher(line).takeIf { it.find() }?.let { m ->
            val mMin = m.group(1)?.toLongOrNull() ?: 0L
            val mSec = m.group(2)?.toLongOrNull() ?: 0L
            val cSec = m.group(3)?.toLongOrNull() ?: 0L
            val txt = m.group(4)?.trim() ?: ""
            return LyricLine(mMin * 60_000 + mSec * 1_000 + cSec * 10, txt)
        }
        lrcTimePattern2.matcher(line).takeIf { it.find() }?.let { m ->
            val h = m.group(1)?.toLongOrNull() ?: 0L
            val min = m.group(2)?.toLongOrNull() ?: 0L
            val sec = m.group(3)?.toLongOrNull() ?: 0L
            val txt2 = m.group(4)?.trim() ?: ""
            return LyricLine(h * 3_600_000 + min * 60_000 + sec * 1_000, txt2)
        }
        return null
    }

    /**
     * 解析 .vtt 文件 - 修复版本
     */
    private fun parseVttFile(file: File): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        val lines = file.readLines()
        var i = 0

        Log.d(TAG, "开始解析VTT文件，共${lines.size}行")

        // 跳过VTT文件头
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line == "WEBVTT" || line.startsWith("NOTE") || line.isEmpty()) {
                i++
                continue
            }
            break
        }

        while (i < lines.size) {
            val line = lines[i].trim()

            // 跳过空行和数字序号行
            if (line.isEmpty() || line.matches("""\d+""".toRegex())) {
                i++
                continue
            }

            // 尝试匹配时间戳行
            var timeMatched = false
            var startTime = 0L

            // 先尝试带小时的格式
            vttTimePatternWithHour.matcher(line).takeIf { it.find() }?.let { m ->
                val h1 = m.group(1)?.toLongOrNull() ?: 0L
                val m1 = m.group(2)?.toLongOrNull() ?: 0L
                val s1 = m.group(3)?.toLongOrNull() ?: 0L
                val ms1 = m.group(4)?.toLongOrNull() ?: 0L
                startTime = h1 * 3_600_000 + m1 * 60_000 + s1 * 1_000 + ms1
                timeMatched = true
                Log.d(TAG, "匹配带小时VTT时间戳: $line -> ${startTime}ms")
            }

            // 再尝试不带小时的格式
            if (!timeMatched) {
                vttTimePattern.matcher(line).takeIf { it.find() }?.let { m ->
                    val m1 = m.group(1)?.toLongOrNull() ?: 0L
                    val s1 = m.group(2)?.toLongOrNull() ?: 0L
                    val ms1 = m.group(3)?.toLongOrNull() ?: 0L
                    startTime = m1 * 60_000 + s1 * 1_000 + ms1
                    timeMatched = true
                    Log.d(TAG, "匹配标准VTT时间戳: $line -> ${startTime}ms")
                }
            }

            if (timeMatched) {
                // 读取歌词文本
                val textBuilder = StringBuilder()
                var j = i + 1

                while (j < lines.size) {
                    val textLine = lines[j].trim()
                    if (textLine.isEmpty()) break

                    // 检查是否是下一个时间戳行
                    if (vttTimePattern.matcher(textLine).find() ||
                        vttTimePatternWithHour.matcher(textLine).find()) {
                        break
                    }

                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(textLine)
                    j++
                }

                val lyricText = textBuilder.toString()
                if (lyricText.isNotEmpty()) {
                    lyrics.add(LyricLine(startTime, lyricText))
                    Log.d(TAG, "添加VTT歌词: ${startTime}ms -> $lyricText")
                }
                i = j
            } else {
                i++
            }
        }

        Log.d(TAG, "VTT解析完成，共${lyrics.size}行歌词")
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