package com.example.animewatcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.animewatcher.api.AnimeProvider
import com.example.animewatcher.api.model.TitleInfo
import com.example.animewatcher.api.provider.ProviderFactory
import com.example.animewatcher.storage.TitleStorage
import com.example.animewatcher.storage.TitleStorageEntry
import com.example.animewatcher.util.Debounce
import com.example.animewatcher.util.Util
import com.example.animewatcher.util.Util.Companion.toast
import com.github.ybq.android.spinkit.SpinKitView
import com.mikepenz.iconics.view.IconicsImageButton
import jp.wasabeef.recyclerview.animators.FadeInAnimator
import jp.wasabeef.recyclerview.animators.LandingAnimator
import jp.wasabeef.recyclerview.animators.ScaleInAnimator
import kotlinx.coroutines.*
import kotlin.collections.ArrayList
import kotlin.system.measureTimeMillis


class SearchActivity : AppCompatActivity() {
    private companion object {
        const val CROSSFADE_DURATION = 500
        const val SEARCH_DELAY = 350L
        const val ITEM_MARGIN = 30
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var imm: InputMethodManager

    private lateinit var loadingView: SpinKitView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: TitleAdapter
    private lateinit var searchView: EditText
    private lateinit var buttonBack: IconicsImageButton
    private lateinit var buttonProviders: IconicsImageButton

    private lateinit var titleStorage: TitleStorage
    private lateinit var provider: AnimeProvider
    private lateinit var providerName: String

    private val titleData = ArrayList<TitleInfo>()
    private val searchDebounces = Debounce(SEARCH_DELAY)
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        val storageMeasure = measureTimeMillis {
            titleStorage = TitleStorage.load(sharedPreferences)
        }
        println("titleStorage loaded in $storageMeasure ms")
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        providerName = sharedPreferences.getString("provider", ProviderFactory.ANIMEPAHE) ?: ProviderFactory.ANIMEPAHE
        provider = ProviderFactory.get(providerName)

        val barViewGroup = LayoutInflater.from(this).inflate(
            R.layout.action_bar_search,
            null, false
        ) as ViewGroup
        searchView = barViewGroup.findViewById(R.id.input_field)
        buttonBack = barViewGroup.findViewById(R.id.button_back)
        buttonProviders = barViewGroup.findViewById(R.id.button_provider)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val layout = ActionBar.LayoutParams(
            ActionBar.LayoutParams.MATCH_PARENT,
            ActionBar.LayoutParams.MATCH_PARENT
        )
        supportActionBar?.setCustomView(barViewGroup, layout)
        supportActionBar?.setDisplayShowCustomEnabled(true)

        searchView.requestFocus()
        searchView.hint = "Search on $providerName"

        buttonBack.setOnClickListener {
            searchDebounces.stop()
            job?.cancel()
            finish()
        }
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchDebounces.stop()
                job?.cancel()
                if (s == null || s.length <= 2) {
                    titleData.clear()
                    adapter.notifyDataSetChanged()
                    return
                }
                searchDebounces.attempt(Runnable {
                    job = coroutineScope.launch {
                        try {
                            loadingView.visibility = View.VISIBLE
                            val results = withContext(Dispatchers.IO) {
                                provider.search(s.toString())
                            }
                            val oldData = titleData
                            if (!isActive) {
                                return@launch
                            }
                            val diff = withContext(Dispatchers.Default) {
                                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                                    override fun areItemsTheSame(
                                        oldPos: Int,
                                        newPos: Int
                                    ): Boolean =
                                        oldData[oldPos].title == results[newPos].title

                                    override fun getOldListSize(): Int = oldData.size

                                    override fun getNewListSize(): Int = results.size

                                    override fun areContentsTheSame(
                                        oldPos: Int,
                                        newPos: Int
                                    ): Boolean = oldData[oldPos] == results[newPos]
                                }, true)
                            }
                            if (!isActive) {
                                return@launch
                            }
                            titleData.clear()
                            titleData.addAll(results)
                            diff.dispatchUpdatesTo(adapter)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            loadingView.visibility = View.INVISIBLE
                        }
                    }
                })

            }

        })
        buttonProviders.setOnClickListener {
            toast(this, "Animepahe is the only provider (not yet implemented)")
        }

        loadingView = findViewById(R.id.loading)

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = FadeInAnimator()
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
//                val pos = parent.getChildAdapterPosition(view)
//                if (pos != 0) {
//                    outRect.top = ITEM_MARGIN
//                }
//                if (pos != parent.adapter!!.itemCount - 1) {
//                    outRect.bottom = ITEM_MARGIN
//                }
                outRect.top = ITEM_MARGIN
            }
        })
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = TitleAdapter(titleData)
        recyclerView.adapter = adapter
    }

    override fun onDestroy() {
        searchDebounces.stop()
        job?.cancel()
        super.onDestroy()
    }

    private class TitleViewHolder(
        val view: ViewGroup,
        val image: ImageView,
        val name: TextView,
        val details: TextView
    ) :
        RecyclerView.ViewHolder(view)

    private inner class TitleAdapter private constructor() :
        RecyclerView.Adapter<TitleViewHolder>() {
        private lateinit var data: List<TitleInfo>

        constructor(data: List<TitleInfo>) : this() {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitleViewHolder {
            val titleView = LayoutInflater.from(parent.context).inflate(
                R.layout.search_title_info, parent,
                false
            ) as ViewGroup
            return TitleViewHolder(
                titleView,
                titleView.findViewById(R.id.title_image),
                titleView.findViewById(R.id.title_name),
                titleView.findViewById(R.id.title_details)
            )
        }

        override fun onBindViewHolder(holder: TitleViewHolder, pos: Int) {
            val item = data[pos]
            Glide
                .with(this@SearchActivity)
                .load(provider.getGlideUrl(item.image ?: ""))
                .placeholder(R.drawable.img)
                .error(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val title = item.title
            val isFavourite = titleStorage.infoList.contains(item)
            holder.name.text = title
            holder.details.text = item.details
            if (isFavourite) {
                holder.view.setBackgroundColor(resources.getColor(R.color.colorHighlight))
            } else {
                holder.view.setBackgroundColor(resources.getColor(R.color.colorPanel))
            }
            holder.view.setOnClickListener {
                if (!isFavourite) {
                    MaterialDialog(this@SearchActivity).show {
                        title(text = "Add to list")
                        message(text = "Do you like to add '$title' to your watching list?")
                        positiveButton(text = "Yes") {
                            titleStorage.update {
                                it.add(TitleStorageEntry(item, providerName))
                                it.sort()
                            }
                            toast(this@SearchActivity, "Added '$title' to watch list")
                            adapter.notifyItemChanged(holder.adapterPosition)
                        }
                        negativeButton(text = "No")
                    }
                } else {
                    val episodesIntent = Intent(this@SearchActivity, EpisodeListActivity::class.java)
                    episodesIntent.putExtra(EpisodeListActivity.ARG, title)
                    startActivity(episodesIntent)
                }
            }
            holder.view.setOnLongClickListener {
                if (isFavourite) {
                    MaterialDialog(this@SearchActivity).show {
                        title(text = "Remove")
                        message(text = "Remove '$title' from your watching list?")
                        positiveButton(text = "Yes") {
                            titleStorage.update {
                                it.remove(titleStorage.findByName(title))
                            }
                            toast(this@SearchActivity,"'$title' removed")
                            adapter.notifyItemChanged(holder.adapterPosition)
                        }
                        negativeButton(text = "No")
                    }
                    return@setOnLongClickListener true
                }
                false
            }
        }

        override fun getItemCount() = data.size
    }
}
