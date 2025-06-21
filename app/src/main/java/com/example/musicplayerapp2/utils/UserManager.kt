package com.example.musicplayerapp2.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 用户管理工具类
 * 负责处理用户登录状态、用户信息存储等功能
 */
class UserManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        // 修改为与 LoginActivity 使用的 SharedPreferences 名称一致
        private const val PREFS_NAME = "login_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val LOGIN_EXPIRY_DAYS = 7

        @Volatile
        private var INSTANCE: UserManager? = null

        fun getInstance(context: Context): UserManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 用户登录
     */
    fun login(username: String, rememberMe: Boolean = false) {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .apply()
    }

    /**
     * 用户登出
     */
    fun logout() {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USERNAME)
            .remove(KEY_LOGIN_TIME)
            .putBoolean(KEY_REMEMBER_ME, false)
            .apply()
    }

    /**
     * 检查用户是否已登录
     */
    fun isLoggedIn(): Boolean {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return false

        // 检查登录是否过期
        val loginTime = sharedPreferences.getLong(KEY_LOGIN_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val expiryTime = loginTime + (LOGIN_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)

        if (currentTime > expiryTime) {
            logout() // 自动登出过期用户
            return false
        }

        return true
    }

    /**
     * 获取当前用户名
     */
    fun getCurrentUsername(): String? {
        return if (isLoggedIn()) {
            sharedPreferences.getString(KEY_USERNAME, null)
        } else {
            null
        }
    }

    /**
     * 获取登录时间
     */
    fun getLoginTime(): Long {
        return sharedPreferences.getLong(KEY_LOGIN_TIME, 0)
    }

    /**
     * 是否记住登录状态
     */
    fun isRememberMeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, false)
    }

    /**
     * 更新登录时间（延长登录状态）
     */
    fun refreshLoginTime() {
        if (isLoggedIn()) {
            sharedPreferences.edit()
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * 获取用户信息摘要
     */
    fun getUserInfo(): UserInfo? {
        return if (isLoggedIn()) {
            UserInfo(
                username = getCurrentUsername() ?: "",
                loginTime = getLoginTime(),
                isRemembered = isRememberMeEnabled()
            )
        } else {
            null
        }
    }

    /**
     * 用户信息数据类
     */
    data class UserInfo(
        val username: String,
        val loginTime: Long,
        val isRemembered: Boolean
    )
}
