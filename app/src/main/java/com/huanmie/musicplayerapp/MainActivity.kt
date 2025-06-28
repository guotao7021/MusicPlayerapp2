package com.huanmie.musicplayerapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.huanmie.musicplayerapp.service.MusicService
import com.huanmie.musicplayerapp.utils.UserManager
import com.huanmie.musicplayerapp.utils.FavoritesManager  // 添加这一行导入
import com.huanmie.musicplayerapp.lyrics.LyricsManager
import com.huanmie.musicplayerapp.views.MiniPlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_READ_STORAGE = 1001
    }

    private lateinit var userManager: UserManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var miniPlayerView: MiniPlayerView
    private var musicService: MusicService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()

            // 绑定最小化播放器到音乐服务
            miniPlayerView.bindMusicService(musicService!!, this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userManager = UserManager.getInstance(this)
        favoritesManager = FavoritesManager.getInstance(this)

        // 初始化最小化播放器
        miniPlayerView = findViewById(R.id.mini_player_view)

        // 无需在这里判断登录，保留 onResume 检查
        userManager.refreshLoginTime()
        setupNavigation()

        // —— 初始化歌词管理器 —— //
        LyricsManager.init(this)

        // —— 请求存储权限，确保能扫描外部歌词文件 —— //
        requestStoragePermissionIfNeeded()

        // 绑定音乐服务
        bindMusicService()
    }

    private fun bindMusicService() {
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
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
                MaterialAlertDialogBuilder(this)
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

            // 使用MaterialAlertDialogBuilder，会自动应用主题中的alertDialogTheme
            MaterialAlertDialogBuilder(this)
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
        MaterialAlertDialogBuilder(this)
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

        // 检查是否需要显示最小化播放器
        musicService?.let { service ->
            if (service.currentSong.value != null && service.isPlaying.value == true) {
                miniPlayerView.show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(connection)
        } catch (e: Exception) {
            // 服务可能已经解绑
        }
    }
}