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
import com.huanmie.musicplayerapp.utils.FavoritesManager
import com.huanmie.musicplayerapp.lyrics.LyricsManager
import com.huanmie.musicplayerapp.views.MiniPlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREF_PERMISSION_REQUESTED = "permission_requested"
    }

    private lateinit var userManager: UserManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var miniPlayerView: MiniPlayerView
    private var musicService: MusicService? = null

    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        handlePermissionResult(isGranted)
    }

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

        userManager.refreshLoginTime()
        setupNavigation()

        // 初始化歌词管理器
        LyricsManager.init(this)

        // 绑定音乐服务
        bindMusicService()

        // 检查并请求权限（仅在首次登录后）
        checkAndRequestPermissionsOnFirstLogin()
    }

    private fun checkAndRequestPermissionsOnFirstLogin() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val permissionRequested = sharedPrefs.getBoolean(PREF_PERMISSION_REQUESTED, false)

        // 如果是首次登录且尚未请求过权限
        if (!permissionRequested) {
            // 标记权限已请求过
            sharedPrefs.edit().putBoolean(PREF_PERMISSION_REQUESTED, true).apply()

            // 请求存储权限
            requestStoragePermission()
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // 权限已授予
                onPermissionGranted()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // 需要显示权限说明
                showPermissionRationaleDialog(permission)
            }
            else -> {
                // 直接请求权限
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun showPermissionRationaleDialog(permission: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("需要存储权限")
            .setMessage("T-Music需要访问您的音乐文件来扫描和播放本地音乐。\n\n权限用途：\n• 扫描设备中的音乐文件\n• 读取音乐文件进行播放\n• 加载专辑封面和歌词文件")
            .setPositiveButton("授予权限") { _, _ ->
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton("暂不授予") { _, _ ->
                handlePermissionResult(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    private fun onPermissionGranted() {
        // 权限已授予，可以自动开始扫描音乐或显示成功提示
        // 这里不做任何操作，让用户在需要时手动扫描
    }

    private fun onPermissionDenied() {
        // 权限被拒绝，显示说明对话框
        MaterialAlertDialogBuilder(this)
            .setTitle("存储权限被拒绝")
            .setMessage("未授予存储权限，将无法扫描并加载歌词文件。\n\n您可以稍后在系统设置中手动开启此权限，或在需要时重新授权。")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页面
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // 如果无法打开设置，显示提示
                    MaterialAlertDialogBuilder(this)
                        .setTitle("提示")
                        .setMessage("请在手机设置 > 应用管理 > T-Music > 权限中开启存储权限")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    private fun bindMusicService() {
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 修复：自定义底部导航点击处理，确保每次点击都重置到对应页面的初始状态
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.allSongsFragment -> {
                    // 清除back stack并导航到全部歌曲页面
                    navController.popBackStack(R.id.allSongsFragment, false)
                    if (navController.currentDestination?.id != R.id.allSongsFragment) {
                        navController.navigate(R.id.allSongsFragment)
                    }
                    true
                }
                R.id.playlistsFragment -> {
                    // 清除back stack并导航到播放列表页面的初始状态
                    navController.popBackStack(R.id.playlistsFragment, false)
                    if (navController.currentDestination?.id != R.id.playlistsFragment) {
                        navController.navigate(R.id.playlistsFragment)
                    }
                    true
                }
                R.id.searchFragment -> {
                    // 清除back stack并导航到搜索页面
                    navController.popBackStack(R.id.searchFragment, false)
                    if (navController.currentDestination?.id != R.id.searchFragment) {
                        navController.navigate(R.id.searchFragment)
                    }
                    true
                }
                else -> false
            }
        }

        // 监听导航变化，同步底部导航选中状态
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.allSongsFragment -> bottomNav.selectedItemId = R.id.allSongsFragment
                R.id.playlistsFragment -> bottomNav.selectedItemId = R.id.playlistsFragment
                R.id.searchFragment -> bottomNav.selectedItemId = R.id.searchFragment
                // 对于其他页面（如播放列表详情、我的最爱），不改变底部导航的选中状态
            }
        }
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