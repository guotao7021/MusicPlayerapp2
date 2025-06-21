package com.example.musicplayerapp2.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayerapp2.PlayerActivity
import com.example.musicplayerapp2.R
import com.example.musicplayerapp2.adapter.SongAdapter
import com.example.musicplayerapp2.data.Playlist
import com.example.musicplayerapp2.data.Song
import com.example.musicplayerapp2.databinding.FragmentPlaylistDetailBinding
import com.example.musicplayerapp2.service.MusicService
import com.example.musicplayerapp2.viewmodel.PlaylistsViewModel
import com.example.musicplayerapp2.viewmodel.PlayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val args: PlaylistDetailFragmentArgs by navArgs()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter
    private var currentPlaylist: Playlist? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbarPlaylistDetail.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                // 播放歌曲
                currentPlaylist?.songs?.let { list ->
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(list))
                        putExtra(MusicService.EXTRA_SONG_INDEX, position)
                    }
                    startActivity(intent)
                }
            },
            onAddToPlaylistClick = null, // 在播放列表详情页不需要添加到播放列表功能
            onRemoveFromPlaylistClick = { song ->
                showRemoveSongDialog(song)
            },
            showRemoveOption = true // 显示删除选项
        )

        binding.rvPlaylistSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songAdapter
        }
    }

    private fun observeViewModel() {
        playlistsViewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            val playlist = playlists.find { it.id == args.playlistId }
            if (playlist != null) {
                currentPlaylist = playlist
                updateUI(playlist)
            } else {
                // 播放列表不存在，返回上级页面
                Toast.makeText(requireContext(), "播放列表不存在", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun updateUI(playlist: Playlist) {
        binding.toolbarPlaylistDetail.title = playlist.name
        binding.tvPlaylistName.text = playlist.name

        val songCount = playlist.songs.size
        binding.tvSongCount.text = when (songCount) {
            0 -> "暂无歌曲"
            1 -> "1 首歌曲"
            else -> "$songCount 首歌曲"
        }

        // 更新歌曲列表
        songAdapter.submitList(playlist.songs)

        // 显示/隐藏空状态
        if (playlist.songs.isEmpty()) {
            binding.rvPlaylistSongs.visibility = View.GONE
            binding.tvNoSongsInPlaylist.visibility = View.VISIBLE
        } else {
            binding.rvPlaylistSongs.visibility = View.VISIBLE
            binding.tvNoSongsInPlaylist.visibility = View.GONE
        }
    }

    /**
     * 显示删除歌曲确认对话框
     */
    private fun showRemoveSongDialog(song: Song) {
        val playlistName = currentPlaylist?.name ?: "播放列表"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移除歌曲")
            .setMessage("确定要从 \"$playlistName\" 中移除歌曲 \"${song.title}\" 吗？")
            .setCancelable(true)
            .setPositiveButton("移除") { _, _ ->
                removeSongFromPlaylist(song)
            }
            .setNegativeButton("取消", null)
            .create()
            .apply {
                show()

                // 设置移除按钮为警告色
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    resources.getColor(R.color.warning, null)
                )
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                    resources.getColor(R.color.textSecondary, null)
                )
            }
    }

    /**
     * 从播放列表中移除歌曲
     */
    private fun removeSongFromPlaylist(song: Song) {
        if (playlistsViewModel.removeSongFromPlaylist(song.id, args.playlistId)) {
            Toast.makeText(
                requireContext(),
                "已从播放列表中移除 \"${song.title}\"",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(requireContext(), "移除失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}