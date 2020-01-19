package com.example.animewatcher.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    val text = MutableLiveData<String>().apply {
        value = ""
    }
}