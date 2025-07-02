package com.huanmie.musicplayerapp.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData

/**
 * 无敏感权限的更新管理器
 * 移除了文件下载功能，专注于引导用户到Google Play更新
 */
class SafeUpdateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SafeUpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_SKIP_VERSION = "skip_version"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L // 24小时

        // 更新信息配置（在发布新版本时需要手动更新这些信息）
        const val LATEST_VERSION_CODE = 4  // 最新版本号
        const val LATEST_VERSION_NAME = "3.1"  // 最新版本名
        const val UPDATE_LOG = """
• 修复了音乐播放的已知问题
• 优化了歌词显示功能  
• 新增了播放列表管理
• 提升了应用性能和稳定性
• 适配了 Android 15 系统
        """.trimIndent()

        @Volatile
        private var INSTANCE: SafeUpdateManager? = null

        fun getInstance(context: Context): SafeUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SafeUpdateManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // LiveData用于观察更新状态
    val updateAvailable = MutableLiveData<UpdateInfo?>()

    /**
     * 检查是否需要检查更新
     */
    fun shouldCheckForUpdate(): Boolean {
        val lastCheckTime = sharedPreferences.getLong(KEY_LAST_CHECK_TIME, 0)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastCheckTime) > CHECK_INTERVAL
    }

    /**
     * 检查是否有新版本可用
     */
    fun checkForUpdate(): UpdateInfo? {
        val currentVersionCode = getCurrentVersionCode()

        Log.d(TAG, "当前版本: ${getCurrentVersionName()} ($currentVersionCode)")
        Log.d(TAG, "最新版本: $LATEST_VERSION_NAME ($LATEST_VERSION_CODE)")

        // 更新最后检查时间
        updateLastCheckTime()

        return if (LATEST_VERSION_CODE > currentVersionCode) {
            // 检查是否被用户跳过
            val skippedVersion = getSkippedVersion()
            if (skippedVersion == LATEST_VERSION_CODE) {
                Log.d(TAG, "用户已跳过版本 $LATEST_VERSION_NAME")
                null
            } else {
                Log.d(TAG, "发现新版本: $LATEST_VERSION_NAME")
                val updateInfo = UpdateInfo(
                    versionCode = LATEST_VERSION_CODE,
                    versionName = LATEST_VERSION_NAME,
                    updateLog = UPDATE_LOG
                )
                updateAvailable.postValue(updateInfo)
                updateInfo
            }
        } else {
            Log.d(TAG, "当前已是最新版本")
            updateAvailable.postValue(null)
            null
        }
    }

    /**
     * 打开Google Play商店
     */
    fun openGooglePlay(): Boolean {
        return try {
            // 尝试打开Google Play应用
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "成功打开Google Play应用")
            true
        } catch (e: Exception) {
            // 如果没有Google Play应用，打开网页版
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "成功打开Google Play网页版")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "无法打开Google Play", e2)
                false
            }
        }
    }

    /**
     * 打开应用设置页面
     * 用户可以在这里手动检查更新或管理应用
     */
    fun openAppSettings(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "成功打开应用设置页面")
            true
        } catch (e: Exception) {
            Log.e(TAG, "无法打开应用设置页面", e)
            false
        }
    }

    /**
     * 跳过当前版本
     */
    fun skipVersion(versionCode: Int) {
        sharedPreferences.edit()
            .putInt(KEY_SKIP_VERSION, versionCode)
            .apply()
        Log.d(TAG, "用户跳过版本: $versionCode")
    }

    /**
     * 清除跳过的版本（用于重置跳过状态）
     */
    fun clearSkippedVersion() {
        sharedPreferences.edit()
            .remove(KEY_SKIP_VERSION)
            .apply()
        Log.d(TAG, "清除跳过版本状态")
    }

    /**
     * 获取应用在Google Play的页面链接
     */
    fun getGooglePlayUrl(): String {
        return "https://play.google.com/store/apps/details?id=${context.packageName}"
    }

    /**
     * 检查是否安装了Google Play商店
     */
    fun isGooglePlayInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.android.vending", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun updateLastCheckTime() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun getSkippedVersion(): Int {
        return sharedPreferences.getInt(KEY_SKIP_VERSION, -1)
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取版本号失败", e)
            1
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "未知"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取版本名失败", e)
            "未知"
        }
    }

    /**
     * 更新信息数据类
     */
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val updateLog: String
    )
}