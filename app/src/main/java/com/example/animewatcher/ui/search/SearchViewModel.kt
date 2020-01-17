package com.example.animewatcher.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Enter title here"
    }
    val text: LiveData<String> = _text
}