package com.huanmie.musicplayerapp.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.huanmie.musicplayerapp.PlayerActivity
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

    // 权限请求
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(requireContext(), "权限已授予", Toast.LENGTH_SHORT).show()
                allSongsViewModel.startScan(requireContext())
            } else {
                Toast.makeText(requireContext(), "需要存储权限来扫描音乐", Toast.LENGTH_SHORT).show()
            }
        }

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
        checkAndRequestPermissions()
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
            binding.noSongsFoundTextView.visibility =
                if (songs.isEmpty()) View.VISIBLE else View.GONE
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupFab() {
        binding.fabScanMusic.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            allSongsViewModel.startScan(requireContext())
        }
    }

    private fun showAddToPlaylistDialog(song: Song) {
        // 获取当前的播放列表数据
        val currentPlaylists = playlistsViewModel.playlists.value

        if (currentPlaylists.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "暂无播放列表，请先创建播放列表", Toast.LENGTH_SHORT).show()
            return
        }

        val playlistNames = currentPlaylists.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("添加到播放列表")
            .setItems(playlistNames) { dialog, which ->
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

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission)
                    == PackageManager.PERMISSION_GRANTED -> {
                // 权限已授予，开始扫描
                allSongsViewModel.startScan(requireContext())
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // 显示权限说明
                AlertDialog.Builder(requireContext())
                    .setTitle("需要存储权限")
                    .setMessage("应用需要访问您的音乐文件来扫描和播放音乐")
                    .setPositiveButton("授予权限") { _, _ ->
                        requestPermissionLauncher.launch(permission)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            else -> {
                // 直接请求权限
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}