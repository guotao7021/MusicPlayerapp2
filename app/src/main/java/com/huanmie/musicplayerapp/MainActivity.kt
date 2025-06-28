package com.huanmie.musicplayerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.huanmie.musicplayerapp.utils.UserManager
import com.huanmie.musicplayerapp.lyrics.LyricsManager

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_READ_STORAGE = 1001
    }

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userManager = UserManager.getInstance(this)

        // 无需在这里判断登录，保留 onResume 检查
        userManager.refreshLoginTime()
        setupNavigation()

        // —— 初始化歌词管理器 —— //
        LyricsManager.init(this)

        // —— 请求存储权限，确保能扫描外部歌词文件 —— //
        requestStoragePermissionIfNeeded()
    }

    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_READ_STORAGE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 存储权限已授予，后续可以正常加载歌词
            } else {
                AlertDialog.Builder(this)
                    .setTitle("存储权限被拒绝")
                    .setMessage("未授予存储权限，将无法扫描并加载歌词文件。")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_user_info -> {
                showUserInfo()
                true
            }
            R.id.menu_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showUserInfo() {
        val userInfo = userManager.getUserInfo()
        userInfo?.let {
            val loginTimeFormatted = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date(it.loginTime))

            AlertDialog.Builder(this)
                .setTitle("用户信息")
                .setMessage("""
                    用户名: ${it.username}
                    登录时间: $loginTimeFormatted
                    记住登录: ${if (it.isRemembered) "是" else "否"}
                """.trimIndent())
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出当前账户吗？")
            .setPositiveButton("确定") { _, _ -> logout() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun logout() {
        userManager.logout()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台检查登录状态，处理超时等情况
        if (!userManager.isLoggedIn()) {
            redirectToLogin()
        }
    }
}
