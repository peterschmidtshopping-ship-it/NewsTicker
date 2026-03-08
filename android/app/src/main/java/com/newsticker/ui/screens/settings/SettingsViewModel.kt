package com.newsticker.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newsticker.data.local.FeedSetting
import com.newsticker.data.local.FeedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val feedStore = FeedStore(application)

    private val _feeds = MutableStateFlow<List<FeedSetting>>(emptyList())
    val feeds: StateFlow<List<FeedSetting>> = _feeds

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    init {
        viewModelScope.launch {
            _feeds.value = feedStore.feeds().first()
        }
    }

    fun updateFeed(index: Int, feed: FeedSetting) {
        val current = _feeds.value.toMutableList()
        if (index in current.indices) {
            current[index] = feed
            _feeds.value = current
        }
    }

    fun save() {
        viewModelScope.launch {
            feedStore.saveFeeds(_feeds.value)
            _saved.value = true
        }
    }
}
