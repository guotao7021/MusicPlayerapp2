package com.example.musicplayerapp2.data

import androidx.lifecycle.MutableLiveData

// MusicDataHolder 单例对象，用于在 Service 和 Activity 之间共享数据
// 使用 LiveData 确保数据变化时 UI 能够自动更新
object MusicDataHolder {
    // LiveData 用于存储随机播放状态
    val isShuffle = MutableLiveData<Boolean>().apply { value = false } // 默认为非随机播放

    // LiveData 用于存储重复模式 (0: 不重复, 1: 单曲重复, 2: 列表重复)
    val repeatMode = MutableLiveData<Int>().apply { value = 0 } // 默认为不重复

    // LiveData 用于存储当前播放位置（毫秒）
    val currentPlaybackPosition = MutableLiveData<Int>().apply { value = 0 }
}
