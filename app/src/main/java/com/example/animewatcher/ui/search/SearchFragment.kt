package com.example.animewatcher.ui.search

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.animewatcher.R
import com.example.animewatcher.api.model.TitleInfo
import com.example.animewatcher.api.provider.AnimePahe
import com.example.animewatcher.util.Debounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.collections.ArrayList

class SearchFragment : Fragment() {
    private companion object {
        val CROSSFADE_DURATION = 1000
        val SEARCH_DELAY = 500L
        val ITEM_MARGIN = 25
    }

    private lateinit var searchViewModel: SearchViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: TitleAdapter
    private val titleData = Collections.synchronizedList(ArrayList<TitleInfo>())
    private val searchDebounces = Debounce(SEARCH_DELAY)
    private val provider = AnimePahe()
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val handler = Handler()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        searchViewModel =
            ViewModelProviders.of(this).get(SearchViewModel::class.java)
            val root = inflater.inflate(R.layout.fragment_search, container, false)
        val searchView: EditText = root.findViewById(R.id.title_input)
        searchViewModel.text.observe(this, Observer {
            searchView.setText(it)
        })

        searchView.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchDebounces.stop()
                job?.cancel()
                if (s != null && s.length > 2) {
                    searchDebounces.attempt(Runnable {
                        job = coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val results = provider.search(s.toString())
                                    titleData.clear()
                                    titleData.addAll(results)
                                    handler.post {
                                        adapter.notifyDataSetChanged()
                                    }
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    })
                }
            }

        })

        recyclerView = root.findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.addItemDecoration(object: RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos != 0) {
                    outRect.top = ITEM_MARGIN
                }
                if (pos != parent.childCount - 1) {
                    outRect.bottom = ITEM_MARGIN
                }
            }
        })
        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        adapter = TitleAdapter(titleData)
        recyclerView.adapter = adapter
        return root
    }

    private class TitleViewHolder(val view: ViewGroup, val image: ImageView, val name: TextView, val details: TextView):
        RecyclerView.ViewHolder(view)

    private inner class TitleAdapter private constructor():
        RecyclerView.Adapter<TitleViewHolder>() {
        private lateinit var data: List<TitleInfo>

        constructor(data: List<TitleInfo>) : this() {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitleViewHolder {
            val titleView = LayoutInflater.from(parent.context).inflate(R.layout.title_info, parent,
                false) as ViewGroup
            return TitleViewHolder(
                titleView,
                titleView.findViewById(R.id.title_image),
                titleView.findViewById(R.id.title_name),
                titleView.findViewById(R.id.title_details)
            )
        }

        override fun onBindViewHolder(holder: TitleViewHolder, pos: Int) {
            Glide
                .with(context!!)
                .load(data[pos].image)
                .apply(RequestOptions.circleCropTransform())
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val title = data[pos].title
            holder.name.text = title
            holder.details.text = "${data[pos].episodeCount} episodes"
            holder.view.setOnClickListener {
                MaterialDialog(context!!).show {
                    title(text = "Add to list")
                    message(text = "Do you like to add '$title' to your watching list?")
                    positiveButton(text = "Yes") {

                    }
                    negativeButton(text = "No")
                }
            }
        }

        override fun getItemCount() = data.size
    }
}