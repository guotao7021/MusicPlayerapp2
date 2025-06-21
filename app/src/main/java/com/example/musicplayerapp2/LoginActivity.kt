package com.example.musicplayerapp2

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayerapp2.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var doubleBackToExitPressedOnce = false

    // 模擬用戶數據
    private val validUsers = mapOf(
        "admin" to "123456",
        "user" to "password",
        "test" to "test123"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        sharedPreferences = getSharedPreferences("login_prefs", MODE_PRIVATE)

        // 【已移除】原有的自動登入檢查邏輯已被移除，確保每次都進入此頁面

        setupUI()
        startEnterAnimations()
    }

    private fun setupUI() {
        // 【新增】預設填入帳號和密碼
        binding.etUsername.setText("admin")
        binding.etPassword.setText("123456")

        binding.etUsername.addTextChangedListener(createTextWatcher())
        binding.etPassword.addTextChangedListener(createTextWatcher())

        binding.btnLogin.setOnClickListener {
            if (!binding.btnLogin.isEnabled) return@setOnClickListener
            handleLogin()
        }

        binding.btnRegister.setOnClickListener {
            Toast.makeText(this, "注册功能即将推出", Toast.LENGTH_SHORT).show()
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "请联系管理员重置密码", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickLogin.setOnClickListener {
            if (!binding.btnQuickLogin.isEnabled) return@setOnClickListener
            binding.etUsername.setText("admin")
            binding.etPassword.setText("123456")
            handleLogin()
        }

        // 由於已預設填入，手動觸發一次按鈕狀態更新
        updateLoginButtonState()
    }

    private fun createTextWatcher(): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLoginButtonState()
            }
        }
    }

    private fun updateLoginButtonState() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        binding.btnLogin.isEnabled = username.isNotEmpty() && password.isNotEmpty()
        binding.btnLogin.alpha = if (binding.btnLogin.isEnabled) 1.0f else 0.6f
    }

    private fun handleLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                if (validateLogin(username, password)) {
                    saveLoginState(username)
                    showLoginSuccess()
                    navigateToMain()
                } else {
                    showLoading(false)
                    showLoginError()
                }
            }
        }, 1000)
    }

    private fun validateLogin(username: String, password: String): Boolean {
        return validUsers[username] == password
    }

    private fun showLoading(isLoading: Boolean) {
        if (::binding.isInitialized) {
            binding.progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
            binding.btnQuickLogin.isEnabled = !isLoading
            binding.btnLogin.alpha = if (isLoading) 0.6f else 1.0f
            binding.btnLogin.text = if (isLoading) "登录中..." else "登录"
        }
    }

    private fun showLoginSuccess() {
        if (::binding.isInitialized) {
            Toast.makeText(this, "登录成功！欢迎使用音乐播放器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoginError() {
        if (::binding.isInitialized) {
            Toast.makeText(this, "用户名或密码错误", Toast.LENGTH_SHORT).show()
            val shakeAnimator = ObjectAnimator.ofFloat(
                binding.layoutLoginForm,
                "translationX",
                0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f
            )
            shakeAnimator.duration = 600
            shakeAnimator.start()
            binding.etPassword.setText("")
            binding.etPassword.requestFocus()
        }
    }

    private fun saveLoginState(username: String) {
        try {
            // 【修復】使用 commit() 確保同步寫入，防止跳轉後狀態還沒更新
            sharedPreferences.edit()
                .putBoolean("is_logged_in", true)
                .putString("username", username)
                .putLong("login_time", System.currentTimeMillis())
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return try {
            val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
            val loginTime = sharedPreferences.getLong("login_time", 0)
            val currentTime = System.currentTimeMillis()
            val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L
            isLoggedIn && (currentTime - loginTime) < sevenDaysInMillis
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun navigateToMain() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "跳转失败，请重试", Toast.LENGTH_SHORT).show()
            showLoading(false)
        }
    }

    private fun startEnterAnimations() {
        if (::binding.isInitialized) {
            try {
                val logoScale = ObjectAnimator.ofFloat(binding.ivLoginLogo, "scaleX", 0f, 1f)
                val logoScale2 = ObjectAnimator.ofFloat(binding.ivLoginLogo, "scaleY", 0f, 1f)
                logoScale.duration = 800
                logoScale2.duration = 800
                logoScale.start()
                logoScale2.start()

                val titleAlpha = ObjectAnimator.ofFloat(binding.tvLoginTitle, "alpha", 0f, 1f)
                titleAlpha.duration = 600
                titleAlpha.startDelay = 300
                titleAlpha.start()

                val formTranslation = ObjectAnimator.ofFloat(binding.layoutLoginForm, "translationY", 200f, 0f)
                val formAlpha = ObjectAnimator.ofFloat(binding.layoutLoginForm, "alpha", 0f, 1f)
                formTranslation.duration = 800
                formAlpha.duration = 800
                formTranslation.startDelay = 500
                formAlpha.startDelay = 500
                formTranslation.start()
                formAlpha.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finishAffinity()
            return
        }
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            binding.ivLoginLogo.clearAnimation()
            binding.tvLoginTitle.clearAnimation()
            binding.layoutLoginForm.clearAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}