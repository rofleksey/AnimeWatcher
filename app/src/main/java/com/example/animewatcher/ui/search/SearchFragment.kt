package com.example.animewatcher.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.example.animewatcher.R

class SearchFragment : Fragment() {

    private lateinit var searchViewModel: SearchViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        searchViewModel =
            ViewModelProviders.of(this).get(SearchViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_search, container, false)
        val searchView: AutoCompleteTextView = root.findViewById(R.id.title_input)
        searchViewModel.text.observe(this, Observer {
            searchView.setText(it)
        })
        return root
    }
}