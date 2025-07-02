package com.huanmie.musicplayerapp.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.databinding.DialogSimpleUpdateBinding
import com.huanmie.musicplayerapp.utils.SafeUpdateManager

/**
 * 安全的更新对话框 - 不涉及敏感权限
 */
class SafeUpdateDialogFragment : DialogFragment() {

    private var _binding: DialogSimpleUpdateBinding? = null
    private val binding get() = _binding!!

    private lateinit var updateManager: SafeUpdateManager
    private lateinit var updateInfo: SafeUpdateManager.UpdateInfo

    companion object {
        private const val ARG_UPDATE_INFO = "update_info"

        fun newInstance(updateInfo: SafeUpdateManager.UpdateInfo): SafeUpdateDialogFragment {
            return SafeUpdateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_UPDATE_INFO, com.google.gson.Gson().toJson(updateInfo))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = SafeUpdateManager.getInstance(requireContext())

        val updateJson = arguments?.getString(ARG_UPDATE_INFO)
        updateInfo = com.google.gson.Gson().fromJson(updateJson, SafeUpdateManager.UpdateInfo::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSimpleUpdateBinding.inflate(layoutInflater)
        setupUI()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(true)
            .create()
    }

    private fun setupUI() {
        with(binding) {
            // 设置版本信息
            tvCurrentVersion.text = "当前版本：${getCurrentVersionName()}"
            tvNewVersion.text = "最新版本：${updateInfo.versionName}"

            // 设置更新日志
            tvUpdateLog.text = updateInfo.updateLog

            // 检查是否安装了Google Play
            val hasGooglePlay = updateManager.isGooglePlayInstalled()

            if (hasGooglePlay) {
                // 有Google Play，显示推荐更新方式
                tvUpdateDescription.text = "推荐通过 Google Play 商店更新，安全可靠："
                btnGooglePlay.text = "打开 Google Play 更新"
            } else {
                // 没有Google Play，引导到网页版或其他方式
                tvUpdateDescription.text = "请通过以下方式获取最新版本："
                btnGooglePlay.text = "打开 Google Play 网页版"
            }

            // 设置按钮点击事件
            btnGooglePlay.setOnClickListener {
                val success = updateManager.openGooglePlay()
                if (success) {
                    dismiss()
                } else {
                    Toast.makeText(context, "无法打开商店，请手动搜索应用更新", Toast.LENGTH_LONG).show()
                }
            }

            btnCopyLink.setOnClickListener {
                copyGooglePlayLink()
            }

            btnAppSettings.setOnClickListener {
                val success = updateManager.openAppSettings()
                if (success) {
                    Toast.makeText(context, "请在应用信息页面检查更新", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
                }
            }

            btnSkip.setOnClickListener {
                updateManager.skipVersion(updateInfo.versionCode)
                Toast.makeText(context, "已跳过版本 ${updateInfo.versionName}", Toast.LENGTH_SHORT).show()
                dismiss()
            }

            btnLater.setOnClickListener {
                dismiss()
            }
        }
    }

    /**
     * 复制Google Play链接到剪贴板
     */
    private fun copyGooglePlayLink() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Google Play链接", updateManager.getGooglePlayUrl())
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "已复制Google Play链接到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "复制链接失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}