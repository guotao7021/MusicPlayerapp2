package com.example.musicplayerapp2

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.musicplayerapp2.utils.UserManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userManager = UserManager.getInstance(this)

        // 【已修復】移除此處多餘的登入檢查，以解決無限跳轉和黑屏問題
        /*
        if (!userManager.isLoggedIn()) {
            redirectToLogin()
            return
        }
        */

        userManager.refreshLoginTime()
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
        if (userInfo != null) {
            val loginTimeFormatted = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date(userInfo.loginTime))

            AlertDialog.Builder(this)
                .setTitle("用户信息")
                .setMessage("""
                    用户名: ${userInfo.username}
                    登录时间: $loginTimeFormatted
                    记住登录: ${if (userInfo.isRemembered) "是" else "否"}
                """.trimIndent())
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出当前账户吗？")
            .setPositiveButton("确定") { _, _ ->
                logout()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun logout() {
        userManager.logout()
        redirectToLogin()
    }

    override fun onResume() {
        super.onResume()
        // 【保留】每次回到前台都检查登录状态，處理登入超時等情況
        if (!userManager.isLoggedIn()) {
            redirectToLogin()
        }
    }
}