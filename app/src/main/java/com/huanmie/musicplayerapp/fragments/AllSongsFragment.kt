package com.huanmie.musicplayerapp.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huanmie.musicplayerapp.PlayerActivity
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.adapter.SongAdapter
import com.huanmie.musicplayerapp.data.Song
import com.huanmie.musicplayerapp.databinding.FragmentAllSongsBinding
import com.huanmie.musicplayerapp.service.MusicService
import com.huanmie.musicplayerapp.viewmodel.AllSongsViewModel
import com.huanmie.musicplayerapp.viewmodel.PlaylistsViewModel

class AllSongsFragment : Fragment() {

    private var _binding: FragmentAllSongsBinding? = null
    private val binding get() = _binding!!

    private val allSongsViewModel: AllSongsViewModel by activityViewModels()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupFab()

        // 检查权限状态，如果已授予则可以开始扫描
        checkPermissionAndScan()
    }

    private fun checkPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，可以开始扫描
            allSongsViewModel.startScan(requireContext())
        } else {
            // 权限未授予，显示提示信息
            showPermissionRequiredState()
        }
    }

    private fun showPermissionRequiredState() {
        // 显示需要权限的状态
        binding.noSongsFoundTextView.text = "需要存储权限来扫描音乐文件"
        binding.noSongsFoundTextView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                // 获取当前歌曲列表
                val songs = allSongsViewModel.allSongs.value ?: emptyList()

                // 启动播放器页面，传递播放列表和当前歌曲索引
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songs))
                    putExtra(MusicService.EXTRA_SONG_INDEX, position)
                }
                startActivity(intent)
            },
            onAddToPlaylistClick = { song ->
                showAddToPlaylistDialog(song)
            }
        )

        binding.recyclerViewAllSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun setupObservers() {
        // 观察歌曲列表变化
        allSongsViewModel.allSongs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)

            if (songs.isEmpty()) {
                // 检查是否有权限来决定显示什么提示信息
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    == PackageManager.PERMISSION_GRANTED) {
                    binding.noSongsFoundTextView.text = "没有找到音乐"
                } else {
                    binding.noSongsFoundTextView.text = "需要存储权限来扫描音乐文件"
                }
                binding.noSongsFoundTextView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.noSongsFoundTextView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupFab() {
        binding.fabScanMusic.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED) {
                // 有权限，开始扫描
                binding.progressBar.visibility = View.VISIBLE

                allSongsViewModel.startScan(requireContext())
            } else {
                // 没有权限，提示用户去设置页面开启权限
                showPermissionRequiredDialog()
            }
        }
    }

    private fun showPermissionRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要存储权限")
            .setMessage("扫描音乐文件需要存储权限。请在设置中授予T-Music存储权限。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "无法打开设置页面", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        // 获取当前的播放列表数据
        val currentPlaylists = playlistsViewModel.playlists.value

        if (currentPlaylists.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "暂无播放列表，请先创建播放列表", Toast.LENGTH_SHORT).show()
            return
        }

        val playlistNames = currentPlaylists.map { it.name }.toTypedArray()

        // Create an ArrayAdapter with a custom layout to ensure correct text color
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dialog_list_item, // Use the new custom layout
            playlistNames
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加到播放列表")
            .setAdapter(adapter) { dialog, which ->
                try {
                    val selectedPlaylist = currentPlaylists[which]
                    if (playlistsViewModel.addSongToPlaylist(song, selectedPlaylist.id)) {
                        Toast.makeText(
                            requireContext(),
                            "已添加到 ${selectedPlaylist.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "歌曲已存在于该播放列表中",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "添加失败，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面时检查权限状态
        checkPermissionAndScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
