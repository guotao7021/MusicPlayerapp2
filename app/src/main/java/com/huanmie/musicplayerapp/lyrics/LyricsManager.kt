package com.huanmie.musicplayerapp.lyrics

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.huanmie.musicplayerapp.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * 歌词行数据模型
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * LyricsManager：用于异步查找并解析 LRC/VTT 歌词文件
 */
class LyricsManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: LyricsManager? = null
        private const val TAG = "LyricsManager"

        /**
         * 必须在 Application 或 Activity 启动时调用一次
         */
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = LyricsManager(context.applicationContext)
                    }
                }
            }
        }

        /**
         * 获取单例实例，未初始化将抛异常
         */
        fun getInstance(): LyricsManager {
            return INSTANCE ?: throw IllegalStateException("请先调用 LyricsManager.init(context) 初始化")
        }
    }

    // LRC 时间戳正则：支持 [hh:mm:ss.xxx]、[mm:ss.xx]、[mm:ss]
    private val lrcTimePattern1 = Pattern.compile("\\[(\\d+):(\\d{2})\\.(\\d{3})\\](.*)")
    private val lrcTimePattern2 = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)")
    private val lrcTimePattern3 = Pattern.compile("\\[(\\d{2}):(\\d{2})\\](.*)")
    private val lrcTimePattern4 = Pattern.compile("\\[(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)")

    // VTT 时间戳正则：支持包含小时和不含小时两种格式
    private val vttPatternWithHour = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{3})"
    )
    private val vttPattern = Pattern.compile(
        "(\\d{1,2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2})\\.(\\d{3})"
    )

    /**
     * 获取歌词（自动切换到 IO 线程）
     */
    suspend fun getLyrics(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val source = findLyricsFile(song)
            Log.d(TAG, "找到的歌词源: $source")
            when (source) {
                is File -> {
                    when (source.extension.lowercase()) {
                        "lrc" -> parseLrcFile(source)
                        "vtt" -> parseVttFile(source)
                        else   -> emptyList<LyricLine>().also { Log.w(TAG, "不支持的歌词文件扩展: ${source.extension}") }
                    }
                }
                is Uri  -> {
                    when {
                        source.toString().endsWith(".lrc", ignoreCase = true) -> parseLrcUri(source)
                        source.toString().endsWith(".vtt", ignoreCase = true) -> parseVttUri(source)
                        else -> parseVttUri(source) // 默认按VTT处理
                    }
                }
                else    -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析歌词时出错", e)
            emptyList()
        }
    }

    /**
     * 查找歌词文件 - 改进的搜索逻辑
     */
    private fun findLyricsFile(song: Song): Any? {
        Log.d(TAG, "开始查找歌词文件，歌曲: ${song.title} - ${song.artist}")
        Log.d(TAG, "音频文件路径: ${song.data}")

        val musicFile = File(song.data)
        val baseName = musicFile.nameWithoutExtension

        // 尝试文件系统扫描（仅在允许的情况下）
        val fileSystemResult = tryFileSystemSearch(musicFile, song)
        if (fileSystemResult != null) {
            Log.d(TAG, "通过文件系统找到歌词: $fileSystemResult")
            return fileSystemResult
        }

        // MediaStore 查询 - 改进的搜索逻辑
        val mediaStoreResult = searchInMediaStore(baseName, song)
        if (mediaStoreResult != null) {
            Log.d(TAG, "通过MediaStore找到歌词: $mediaStoreResult")
            return mediaStoreResult
        }

        // 尝试通过DocumentsContract查找（Android 10+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val documentsResult = searchInDocuments(baseName, song)
            if (documentsResult != null) {
                Log.d(TAG, "通过Documents找到歌词: $documentsResult")
                return documentsResult
            }
        }

        Log.w(TAG, "未找到匹配的歌词文件")
        return null
    }

    /**
     * 尝试文件系统搜索
     */
    private fun tryFileSystemSearch(musicFile: File, song: Song): File? {
        return try {
            musicFile.parentFile?.let { parentDir ->
                if (parentDir.exists() && parentDir.canRead()) {
                    Log.d(TAG, "扫描目录: ${parentDir.absolutePath}")
                    val allFiles = parentDir.listFiles()
                    Log.d(TAG, "目录中共有 ${allFiles?.size ?: 0} 个文件")

                    // 列出所有 .lrc 和 .vtt 文件
                    allFiles?.filter {
                        it.isFile && (it.extension.lowercase() == "lrc" || it.extension.lowercase() == "vtt")
                    }?.forEach { file ->
                        Log.d(TAG, "发现歌词文件: ${file.name}")
                    }

                    val candidates = findLyricsCandidates(parentDir, song)
                    candidates.firstOrNull()
                } else {
                    Log.d(TAG, "无法读取目录: ${parentDir.absolutePath}")
                    null
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "文件系统访问被拒绝: $e")
            null
        }
    }

    /**
     * 在目录中查找歌词文件候选 - 改进VTT文件匹配
     */
    private fun findLyricsCandidates(directory: File, song: Song): List<File> {
        val candidates = mutableListOf<File>()
        val musicFile = File(song.data)
        val baseName = musicFile.nameWithoutExtension.lowercase()
        val titleLower = song.title.lowercase().trim()
        val artistLower = song.artist.lowercase().trim()
        val exts = listOf("lrc", "vtt")

        try {
            directory.listFiles { file ->
                file.isFile && exts.contains(file.extension.lowercase())
            }?.forEach { lyricFile ->
                Log.d(TAG, "检查文件: ${lyricFile.name}")
                val fileName = lyricFile.nameWithoutExtension.lowercase()
                val score = calculateMatchScore(fileName, baseName, titleLower, artistLower)
                if (score > 0) {
                    candidates.add(lyricFile)
                    Log.d(TAG, "找到候选文件: ${lyricFile.name}, 匹配分数: $score, 类型: ${lyricFile.extension}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "扫描目录时出错: $e")
        }

        Log.d(TAG, "总共找到 ${candidates.size} 个候选文件")

        // 按匹配分数排序，VTT文件优先级稍高（因为通常更准确）
        return candidates.sortedWith(compareByDescending<File> { file ->
            val baseScore = calculateMatchScore(file.nameWithoutExtension.lowercase(), baseName, titleLower, artistLower)
            // VTT文件额外加分
            if (file.extension.lowercase() == "vtt") baseScore + 5 else baseScore
        })
    }

    /**
     * 计算文件名匹配分数 - 改进匹配算法
     */
    private fun calculateMatchScore(fileName: String, baseName: String, title: String, artist: String): Int {
        var score = 0

        // 移除语言标识符后的文件名（如 .zh, .en, .cn 等）
        val cleanFileName = removeLanguageIdentifier(fileName)
        val cleanBaseName = removeLanguageIdentifier(baseName)

        Log.d(TAG, "匹配检查: 文件名='$fileName' -> 清理后='$cleanFileName', 基础名='$baseName' -> 清理后='$cleanBaseName'")

        // 完全匹配基础文件名（最高分）
        if (cleanFileName == cleanBaseName) {
            score += 100
            Log.d(TAG, "完全匹配基础文件名，得分+100")
        }

        // 基础名包含关系
        if (cleanFileName.contains(cleanBaseName) || cleanBaseName.contains(cleanFileName)) {
            score += 90
            Log.d(TAG, "基础名包含关系，得分+90")
        }

        // 包含完整标题
        if (title.isNotEmpty() && cleanFileName.contains(title)) {
            score += 80
            Log.d(TAG, "包含完整标题，得分+80")
        }

        // 包含完整艺术家名
        if (artist.isNotEmpty() && cleanFileName.contains(artist)) {
            score += 60
            Log.d(TAG, "包含完整艺术家名，得分+60")
        }

        // 标题和艺术家都包含
        if (title.isNotEmpty() && artist.isNotEmpty() &&
            cleanFileName.contains(title) && cleanFileName.contains(artist)) {
            score += 40
            Log.d(TAG, "标题和艺术家都包含，得分+40")
        }

        // 部分匹配（至少4个字符）
        if (cleanBaseName.length >= 4 && cleanFileName.contains(cleanBaseName.substring(0, 4))) {
            score += 20
            Log.d(TAG, "部分匹配，得分+20")
        }

        Log.d(TAG, "最终匹配分数: $score")
        return score
    }

    /**
     * 移除文件名中的语言标识符
     */
    private fun removeLanguageIdentifier(fileName: String): String {
        // 常见的语言标识符模式：.zh, .en, .cn, .chs, .cht 等
        val languagePatterns = listOf(
            "\\.zh$", "\\.en$", "\\.cn$", "\\.chs$", "\\.cht$",
            "\\.chinese$", "\\.english$", "\\.sc$", "\\.tc$"
        )

        var cleanName = fileName
        for (pattern in languagePatterns) {
            cleanName = cleanName.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }

        return cleanName
    }

    /**
     * 在MediaStore中搜索 - 改进VTT文件查询
     */
    private fun searchInMediaStore(baseName: String, song: Song): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // 构建更灵活的查询条件，包含VTT文件
        val titlePattern = "%${song.title.replace("'", "''")}%"
        val artistPattern = "%${song.artist.replace("'", "''")}%"
        val basePattern = "%$baseName%"

        val selection = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 搜索 .lrc 和 .vtt 文件
        selection.append("(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ")
        selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ")
        selectionArgs.add("$basePattern.lrc")
        selectionArgs.add("$basePattern.vtt")

        // 添加标题和艺术家匹配
        if (song.title.isNotEmpty()) {
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ")
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ")
            selectionArgs.add("$titlePattern.lrc")
            selectionArgs.add("$titlePattern.vtt")
        }

        if (song.artist.isNotEmpty()) {
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ")
            selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
            selectionArgs.add("$artistPattern.lrc")
            selectionArgs.add("$artistPattern.vtt")
        }

        // 移除最后的 OR 并添加文件扩展名限制
        if (selection.endsWith(" OR ")) {
            selection.setLength(selection.length - 4)
        }
        selection.append(") AND (")
        selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.lrc' OR ")
        selection.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.vtt'")
        selection.append(")")

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        return try {
            Log.d(TAG, "MediaStore查询条件: $selection")
            Log.d(TAG, "MediaStore查询参数: $selectionArgs")

            resolver.query(
                uri,
                projection,
                selection.toString(),
                selectionArgs.toTypedArray(),
                null
            )?.use { cursor ->
                findBestMatchFromCursor(cursor, baseName, song.title, song.artist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore查询失败: $e")
            null
        }
    }

    /**
     * 从Cursor中找到最佳匹配
     */
    private fun findBestMatchFromCursor(cursor: Cursor, baseName: String, title: String, artist: String): Uri? {
        var bestMatch: Pair<Uri, Int>? = null
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val displayName = cursor.getString(nameColumn) ?: continue
            val fileName = displayName.substringBeforeLast('.').lowercase()
            val extension = displayName.substringAfterLast('.').lowercase()

            val baseScore = calculateMatchScore(fileName, baseName.lowercase(), title.lowercase(), artist.lowercase())
            // VTT文件额外加分
            val score = if (extension == "vtt") baseScore + 5 else baseScore

            if (score > 0 && (bestMatch == null || score > bestMatch.second)) {
                val fileUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                bestMatch = Pair(fileUri, score)
                Log.d(TAG, "找到匹配文件: $displayName, 分数: $score, 类型: $extension")
            }
        }

        return bestMatch?.first
    }

    /**
     * 在Documents中搜索（Android 10+的补充方法）
     */
    private fun searchInDocuments(baseName: String, song: Song): Uri? {
        // 这里可以实现基于DocumentsContract的搜索
        // 由于复杂性，暂时返回null，后续可以扩展
        return null
    }

    /**
     * 解析 LRC 文件
     */
    private fun parseLrcFile(file: File): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        file.readLines().forEach { parseLrcLine(it.trim())?.let(list::add) }
        Log.d(TAG, "LRC解析完成，共${list.size}行歌词")
        return list.sortedBy { it.timeMs }
    }

    /**
     * 逐行解析 LRC
     */
    private fun parseLrcLine(line: String): LyricLine? {
        if (line.startsWith("[ti:") || line.contains("作词") || line.contains("作曲")) return null
        lrcTimePattern1.matcher(line).takeIf { it.find() }?.let { m ->
            val min = m.group(1)?.toLongOrNull() ?: 0L
            val sec = m.group(2)?.toLongOrNull() ?: 0L
            val ms  = m.group(3)?.toLongOrNull() ?: 0L
            val txt = m.group(4)?.trim() ?: ""
            if (txt.isNotEmpty()) return LyricLine(min * 60000 + sec * 1000 + ms, txt)
        }
        lrcTimePattern2.matcher(line).takeIf { it.find() }?.let { m ->
            val min = m.group(1)?.toLongOrNull() ?: 0L
            val sec = m.group(2)?.toLongOrNull() ?: 0L
            val txt = m.group(3)?.trim() ?: ""
            if (txt.isNotEmpty()) return LyricLine(min * 60000 + sec * 1000, txt)
        }
        lrcTimePattern3.matcher(line).takeIf { it.find() }?.let { m ->
            val min = m.group(1)?.toLongOrNull() ?: 0L
            val sec = m.group(2)?.toLongOrNull() ?: 0L
            val txt = m.group(3)?.trim() ?: ""
            if (txt.isNotEmpty()) return LyricLine(min * 60000 + sec * 1000, txt)
        }
        lrcTimePattern4.matcher(line).takeIf { it.find() }?.let { m ->
            val h   = m.group(1)?.toLongOrNull() ?: 0L
            val min = m.group(2)?.toLongOrNull() ?: 0L
            val sec = m.group(3)?.toLongOrNull() ?: 0L
            val ms  = m.group(4)?.toLongOrNull() ?: 0L
            val txt = m.group(5)?.trim() ?: ""
            if (txt.isNotEmpty()) return LyricLine(h * 3600000 + min * 60000 + sec * 1000 + ms, txt)
        }
        return null
    }

    /**
     * 解析 VTT 文件（本地） - 改进的VTT解析逻辑
     */
    private fun parseVttFile(file: File): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        Log.d(TAG, "开始解析VTT文件: ${file.name}")

        try {
            val lines = file.readLines()
            var i = 0

            // 跳过WEBVTT头部
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("WEBVTT") || line.isEmpty()) {
                    i++
                    continue
                }
                break
            }

            while (i < lines.size) {
                val line = lines[i++].trim()

                // 跳过空行和序号行
                if (line.isEmpty() || line.matches("\\d+".toRegex())) {
                    continue
                }

                var startTime = 0L
                var foundTimeStamp = false

                // 尝试解析时间戳
                vttPatternWithHour.matcher(line).takeIf { it.find() }?.let { m ->
                    val h  = m.group(1)?.toLongOrNull() ?: 0L
                    val mm = m.group(2)?.toLongOrNull() ?: 0L
                    val ss = m.group(3)?.toLongOrNull() ?: 0L
                    val ms = m.group(4)?.toLongOrNull() ?: 0L
                    startTime = h * 3600000 + mm * 60000 + ss * 1000 + ms
                    foundTimeStamp = true
                    Log.d(TAG, "解析时间戳(带小时): ${m.group(0)} -> ${startTime}ms")
                } ?: vttPattern.matcher(line).takeIf { it.find() }?.let { m ->
                    val mm = m.group(1)?.toLongOrNull() ?: 0L
                    val ss = m.group(2)?.toLongOrNull() ?: 0L
                    val ms = m.group(3)?.toLongOrNull() ?: 0L
                    startTime = mm * 60000 + ss * 1000 + ms
                    foundTimeStamp = true
                    Log.d(TAG, "解析时间戳(无小时): ${m.group(0)} -> ${startTime}ms")
                }

                if (foundTimeStamp) {
                    // 读取字幕文本
                    val textBuilder = StringBuilder()
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        val textLine = lines[i++].trim()
                        // 移除VTT标签
                        val cleanText = removeVttTags(textLine)
                        if (cleanText.isNotEmpty()) {
                            if (textBuilder.isNotEmpty()) {
                                textBuilder.append(" ")
                            }
                            textBuilder.append(cleanText)
                        }
                    }

                    val text = textBuilder.toString().trim()
                    if (text.isNotEmpty()) {
                        list.add(LyricLine(startTime, text))
                        Log.d(TAG, "添加歌词行: $startTime ms -> $text")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析VTT文件时出错: $e")
        }

        Log.d(TAG, "VTT解析完成，共${list.size}行歌词")
        return list.sortedBy { it.timeMs }
    }

    /**
     * 移除VTT标签
     */
    private fun removeVttTags(text: String): String {
        // 移除VTT格式标签，如 <c.yellow>text</c>
        return text.replace(Regex("<[^>]*>"), "").trim()
    }

    /**
     * 解析 LRC 文件（URI）
     */
    private suspend fun parseLrcUri(uri: Uri): List<LyricLine> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LyricLine>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEach { line ->
                        parseLrcLine(line.trim())?.let(list::add)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析LRC URI时出错: $e")
        }
        Log.d(TAG, "LRC URI解析完成，共${list.size}行歌词")
        return@withContext list.sortedBy { it.timeMs }
    }

    /**
     * 解析 VTT 文件（URI） - 改进的VTT URI解析
     */
    private suspend fun parseVttUri(uri: Uri): List<LyricLine> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LyricLine>()
        Log.d(TAG, "开始解析VTT URI: $uri")

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val lines = reader.readLines()
                    var i = 0

                    // 跳过WEBVTT头部
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.startsWith("WEBVTT") || line.isEmpty()) {
                            i++
                            continue
                        }
                        break
                    }

                    while (i < lines.size) {
                        val line = lines[i++].trim()

                        // 跳过空行和序号行
                        if (line.isEmpty() || line.matches("\\d+".toRegex())) {
                            continue
                        }

                        var startTime = 0L
                        var foundTimeStamp = false

                        // 尝试解析时间戳
                        vttPatternWithHour.matcher(line).takeIf { it.find() }?.let { m ->
                            val h  = m.group(1)?.toLongOrNull() ?: 0L
                            val mm = m.group(2)?.toLongOrNull() ?: 0L
                            val ss = m.group(3)?.toLongOrNull() ?: 0L
                            val ms = m.group(4)?.toLongOrNull() ?: 0L
                            startTime = h * 3600000 + mm * 60000 + ss * 1000 + ms
                            foundTimeStamp = true
                            Log.d(TAG, "解析URI时间戳(带小时): ${m.group(0)} -> ${startTime}ms")
                        } ?: vttPattern.matcher(line).takeIf { it.find() }?.let { m ->
                            val mm = m.group(1)?.toLongOrNull() ?: 0L
                            val ss = m.group(2)?.toLongOrNull() ?: 0L
                            val ms = m.group(3)?.toLongOrNull() ?: 0L
                            startTime = mm * 60000 + ss * 1000 + ms
                            foundTimeStamp = true
                            Log.d(TAG, "解析URI时间戳(无小时): ${m.group(0)} -> ${startTime}ms")
                        }

                        if (foundTimeStamp) {
                            // 读取字幕文本
                            val textBuilder = StringBuilder()
                            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                                val textLine = lines[i++].trim()
                                // 移除VTT标签
                                val cleanText = removeVttTags(textLine)
                                if (cleanText.isNotEmpty()) {
                                    if (textBuilder.isNotEmpty()) {
                                        textBuilder.append(" ")
                                    }
                                    textBuilder.append(cleanText)
                                }
                            }

                            val text = textBuilder.toString().trim()
                            if (text.isNotEmpty()) {
                                list.add(LyricLine(startTime, text))
                                Log.d(TAG, "添加URI歌词行: $startTime ms -> $text")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析VTT URI时出错: $e")
        }

        Log.d(TAG, "VTT URI解析完成，共${list.size}行歌词")
        return@withContext list.sortedBy { it.timeMs }
    }

    /**
     * 根据当前播放时间获取高亮行索引
     */
    fun getCurrentLyricIndex(lyrics: List<LyricLine>, currentTimeMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        for ((index, line) in lyrics.withIndex()) {
            if (line.timeMs > currentTimeMs) return index - 1
        }
        return lyrics.lastIndex
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
            LyricLine(20_000, "例如: 歌曲.mp3 -> 歌曲.lrc 或 歌曲.vtt"),
            LyricLine(25_000, "支持标准VTT格式歌词文件"),
            LyricLine(30_000, "点击任意歌词可跳转播放"),
            LyricLine(35_000, "点击专辑封面切换歌词显示")
        )
    }
}