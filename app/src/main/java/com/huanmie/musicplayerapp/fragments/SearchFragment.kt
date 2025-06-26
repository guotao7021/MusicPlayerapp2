package com.huanmie.musicplayerapp.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.huanmie.musicplayerapp.PlayerActivity
import com.huanmie.musicplayerapp.adapter.SongAdapter
import com.huanmie.musicplayerapp.data.Song
import com.huanmie.musicplayerapp.databinding.FragmentSearchBinding
import com.huanmie.musicplayerapp.viewmodel.AllSongsViewModel
import com.huanmie.musicplayerapp.service.MusicService

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val allSongsViewModel: AllSongsViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onItemClick = { song: Song, position: Int ->
                // 获取当前搜索结果列表
                val currentList = songAdapter.currentList
                val actualIndex = currentList.indexOf(song)

                if (actualIndex != -1) {
                    // 启动播放器页面，传递搜索结果列表和歌曲索引
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(currentList))
                        putExtra(MusicService.EXTRA_SONG_INDEX, actualIndex)
                    }
                    startActivity(intent)
                }
            },
            onAddToPlaylistClick = null // 搜索页面暂时不提供添加到播放列表功能
        )

        binding.rvSearchResults.layoutManager = LinearLayoutManager(context)
        binding.rvSearchResults.adapter = songAdapter
    }

    private fun setupSearch() {
        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        val allSongs = allSongsViewModel.allSongs.value.orEmpty()

        if (query.isBlank()) {
            // 查询为空时，清空搜索结果
            songAdapter.submitList(emptyList())
            binding.tvSearchResultsPlaceholder.visibility = View.VISIBLE
            binding.tvSearchResultsPlaceholder.text = "输入关键词进行搜索"
            binding.rvSearchResults.visibility = View.GONE
        } else {
            // 执行搜索
            binding.tvSearchResultsPlaceholder.visibility = View.GONE
            val filteredSongs = allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
            }

            songAdapter.submitList(filteredSongs)

            if (filteredSongs.isEmpty()) {
                binding.rvSearchResults.visibility = View.GONE
                binding.tvSearchResultsPlaceholder.visibility = View.VISIBLE
                binding.tvSearchResultsPlaceholder.text = "无搜索结果"
            } else {
                binding.rvSearchResults.visibility = View.VISIBLE
                binding.tvSearchResultsPlaceholder.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}