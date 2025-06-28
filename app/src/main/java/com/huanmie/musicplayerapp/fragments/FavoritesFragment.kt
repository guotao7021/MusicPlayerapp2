package com.huanmie.musicplayerapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.huanmie.musicplayerapp.PlayerActivity
import com.huanmie.musicplayerapp.R
import com.huanmie.musicplayerapp.adapter.SongAdapter
import com.huanmie.musicplayerapp.data.Song
import com.huanmie.musicplayerapp.databinding.FragmentFavoritesBinding
import com.huanmie.musicplayerapp.service.MusicService
import com.huanmie.musicplayerapp.utils.FavoritesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        favoritesManager = FavoritesManager.getInstance(requireContext())

        setupToolbar()
        setupRecyclerView()
        observeFavorites()
    }

    private fun setupToolbar() {
        // 返回按钮点击
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 更多菜单按钮点击
        binding.btnMoreMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)

        // 手动添加菜单项
        popup.menu.add(0, 1, 0, "返回播放列表")
        popup.menu.add(0, 2, 1, "清空我的最爱")

        // 设置点击监听
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    findNavController().popBackStack()
                    true
                }
                2 -> {
                    showClearFavoritesDialog()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                // 播放我的最爱列表
                val favoritesList = favoritesManager.getFavorites()
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(favoritesList))
                    putExtra(MusicService.EXTRA_SONG_INDEX, position)
                }
                startActivity(intent)
            },
            onAddToPlaylistClick = null, // 在我的最爱页面不需要添加到播放列表功能
            onRemoveFromPlaylistClick = { song ->
                showRemoveFromFavoritesDialog(song)
            },
            showRemoveOption = true // 显示移除选项
        )

        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songAdapter
        }
    }

    private fun observeFavorites() {
        favoritesManager.favoriteSongs.observe(viewLifecycleOwner) { favorites ->
            updateUI(favorites)
        }
    }

    private fun updateUI(favorites: List<Song>) {
        binding.tvToolbarTitle.text = "我的最爱"

        val songCount = favorites.size
        binding.tvFavoritesCount.text = when (songCount) {
            0 -> "暂无喜爱的歌曲"
            1 -> "1 首歌曲"
            else -> "$songCount 首歌曲"
        }

        // 更新歌曲列表
        songAdapter.submitList(favorites)

        // 显示/隐藏空状态
        if (favorites.isEmpty()) {
            binding.rvFavorites.visibility = View.GONE
            binding.layoutEmptyFavorites.visibility = View.VISIBLE
        } else {
            binding.rvFavorites.visibility = View.VISIBLE
            binding.layoutEmptyFavorites.visibility = View.GONE
        }
    }

    /**
     * 显示从我的最爱中移除歌曲的确认对话框
     */
    private fun showRemoveFromFavoritesDialog(song: Song) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移除歌曲")
            .setMessage("确定要从我的最爱中移除歌曲 \"${song.title}\" 吗？")
            .setCancelable(true)
            .setPositiveButton("移除") { _, _ ->
                removeSongFromFavorites(song)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示清空我的最爱的确认对话框
     */
    private fun showClearFavoritesDialog() {
        val favoritesCount = favoritesManager.getFavoritesCount()
        if (favoritesCount == 0) {
            Toast.makeText(requireContext(), "我的最爱列表为空", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清空我的最爱")
            .setMessage("确定要清空我的最爱吗？这将移除所有 $favoritesCount 首歌曲。")
            .setCancelable(true)
            .setPositiveButton("清空") { _, _ ->
                clearAllFavorites()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 从我的最爱中移除歌曲
     */
    private fun removeSongFromFavorites(song: Song) {
        if (favoritesManager.removeFromFavorites(song)) {
            Toast.makeText(
                requireContext(),
                "已从我的最爱中移除 \"${song.title}\"",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(requireContext(), "移除失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 清空我的最爱
     */
    private fun clearAllFavorites() {
        favoritesManager.clearFavorites()
        Toast.makeText(requireContext(), "已清空我的最爱", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}