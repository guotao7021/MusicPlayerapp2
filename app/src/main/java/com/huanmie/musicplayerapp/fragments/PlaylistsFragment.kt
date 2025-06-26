package com.huanmie.musicplayerapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.adapter.PlaylistAdapter
import com.huanmie.musicplayerapp.data.Playlist
import com.huanmie.musicplayerapp.databinding.DialogCreatePlaylistBinding
import com.huanmie.musicplayerapp.databinding.FragmentPlaylistsBinding
import com.huanmie.musicplayerapp.viewmodel.PlaylistsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private val playlistsViewModel: PlaylistsViewModel by activityViewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }
    private lateinit var playlistAdapter: PlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            playlists = mutableListOf(),
            onItemClick = { playlist ->
                // 跳转到播放列表详情页面
                val action = PlaylistsFragmentDirections
                    .actionPlaylistsFragmentToPlaylistDetailFragment(playlist.id)
                findNavController().navigate(action)
            },
            onRenameClick = { playlist ->
                showRenamePlaylistDialog(playlist)
            },
            onDeleteClick = { playlist ->
                showDeletePlaylistDialog(playlist)
            }
        )
        binding.rvPlaylists.layoutManager = LinearLayoutManager(context)
        binding.rvPlaylists.adapter = playlistAdapter
    }

    private fun observeViewModel() {
        playlistsViewModel.playlists.observe(viewLifecycleOwner) { lists ->
            playlistAdapter.updateData(lists)

            // 切换空状态显示
            if (lists.isEmpty()) {
                binding.rvPlaylists.visibility = View.GONE
                binding.emptyPlaylistsLayout.visibility = View.VISIBLE
            } else {
                binding.rvPlaylists.visibility = View.VISIBLE
                binding.emptyPlaylistsLayout.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        // 添加播放列表按钮
        binding.fabAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        // 功能卡片点击事件
        binding.cardMyFavorites.setOnClickListener {
            Toast.makeText(requireContext(), "我的最爱功能开发中", Toast.LENGTH_SHORT).show()
        }

        binding.cardMyCollections.setOnClickListener {
            Toast.makeText(requireContext(), "我的收藏功能开发中", Toast.LENGTH_SHORT).show()
        }

        binding.cardRecentlyPlayed.setOnClickListener {
            Toast.makeText(requireContext(), "最近播放功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示创建播放列表对话框
     */
    private fun showCreatePlaylistDialog() {
        val dlgBinding = DialogCreatePlaylistBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建播放列表")
            .setView(dlgBinding.root)
            .setCancelable(true)
            .setPositiveButton("创建") { _, _ ->
                val name = dlgBinding.etPlaylistName.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (playlistsViewModel.isPlaylistNameAvailable(name)) {
                        playlistsViewModel.createPlaylist(name)
                        Toast.makeText(requireContext(), "播放列表 \"$name\" 创建成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "播放列表名称已存在", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "请输入播放列表名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 自动聚焦到输入框
        dlgBinding.etPlaylistName.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // 设置按钮颜色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            resources.getColor(R.color.colorPrimary, null)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            resources.getColor(R.color.textSecondary, null)
        )
    }

    /**
     * 显示重命名播放列表对话框
     */
    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val editText = EditText(requireContext()).apply {
            setText(playlist.name)
            setTextColor(resources.getColor(R.color.white, null))
            setHintTextColor(resources.getColor(R.color.gray_light, null))

            // 尝试使用背景drawable，如果不存在则使用默认背景
            try {
                background = resources.getDrawable(R.drawable.bg_edittext_rounded, null)
            } catch (e: Exception) {
                // 如果背景文件不存在，使用默认背景
                setBackgroundColor(resources.getColor(R.color.gray_darker, null))
            }

            setPadding(32, 24, 32, 24)
            selectAll() // 选中所有文字便于修改
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("重命名播放列表")
            .setView(editText)
            .setCancelable(true)
            .setPositiveButton("重命名") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (playlistsViewModel.isPlaylistNameAvailable(newName, playlist.id)) {
                        if (playlistsViewModel.renamePlaylist(playlist.id, newName)) {
                            Toast.makeText(requireContext(), "播放列表已重命名为 \"$newName\"", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "重命名失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "播放列表名称已存在", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "请输入新的播放列表名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 自动聚焦并显示键盘
        editText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        // 设置按钮颜色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            resources.getColor(R.color.colorPrimary, null)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            resources.getColor(R.color.textSecondary, null)
        )
    }

    /**
     * 显示删除播放列表确认对话框
     */
    private fun showDeletePlaylistDialog(playlist: Playlist) {
        val songCount = playlist.songs.size
        val message = if (songCount > 0) {
            "确定要删除播放列表 \"${playlist.name}\" 吗？\n\n此操作将删除播放列表中的 $songCount 首歌曲。"
        } else {
            "确定要删除播放列表 \"${playlist.name}\" 吗？"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除播放列表")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("删除") { _, _ ->
                if (playlistsViewModel.deletePlaylist(playlist.id)) {
                    Toast.makeText(requireContext(), "播放列表 \"${playlist.name}\" 已删除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
            .apply {
                show()

                // 设置删除按钮为红色
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    resources.getColor(R.color.error, null)
                )
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                    resources.getColor(R.color.textSecondary, null)
                )
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}