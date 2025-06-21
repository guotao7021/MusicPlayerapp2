package com.example.musicplayerapp2

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayerapp2.databinding.ActivitySplashBinding


class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashTimeOut = 3000L // 3秒启动动画

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 隐藏状态栏和导航栏，全屏显示
        supportActionBar?.hide()

        // 启动动画
        startAnimations()

        // 延时跳转到登录页面
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToLogin()
        }, splashTimeOut)
    }

    private fun startAnimations() {
        // Logo 缩放动画
        val scaleXAnimator = ObjectAnimator.ofFloat(binding.ivAppLogo, "scaleX", 0.5f, 1.2f, 1.0f)
        val scaleYAnimator = ObjectAnimator.ofFloat(binding.ivAppLogo, "scaleY", 0.5f, 1.2f, 1.0f)

        scaleXAnimator.duration = 2000
        scaleYAnimator.duration = 2000
        scaleXAnimator.interpolator = AccelerateDecelerateInterpolator()
        scaleYAnimator.interpolator = AccelerateDecelerateInterpolator()

        scaleXAnimator.start()
        scaleYAnimator.start()

        // Logo 旋转动画
        val rotationAnimator = ObjectAnimator.ofFloat(binding.ivAppLogo, "rotation", 0f, 360f)
        rotationAnimator.duration = 2000
        rotationAnimator.interpolator = AccelerateDecelerateInterpolator()
        rotationAnimator.start()

        // 标题淡入动画
        val alphaAnimator = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f)
        alphaAnimator.duration = 1500
        alphaAnimator.startDelay = 500
        alphaAnimator.start()

        // 副标题向上滑动动画
        val translationAnimator = ObjectAnimator.ofFloat(binding.tvAppTagline, "translationY", 100f, 0f)
        val alphaAnimator2 = ObjectAnimator.ofFloat(binding.tvAppTagline, "alpha", 0f, 1f)
        translationAnimator.duration = 1000
        alphaAnimator2.duration = 1000
        translationAnimator.startDelay = 1000
        alphaAnimator2.startDelay = 1000
        translationAnimator.start()
        alphaAnimator2.start()

        // 进度条动画
        val progressAnimator = ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100)
        progressAnimator.duration = 2500
        progressAnimator.startDelay = 300
        progressAnimator.start()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
        // 添加过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onBackPressed() {
        // 在启动页面禁用返回键
        // 不调用 super.onBackPressed()
    }
}