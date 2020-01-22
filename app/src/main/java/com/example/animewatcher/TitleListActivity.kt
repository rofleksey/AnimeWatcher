package com.example.animewatcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.animewatcher.api.provider.AnimePahe
import com.example.animewatcher.api.provider.ProviderFactory
import com.example.animewatcher.storage.TitleStorage
import com.example.animewatcher.storage.TitleStorageEntry
import com.example.animewatcher.util.Util
import com.github.ybq.android.spinkit.SpinKitView
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import com.mikepenz.iconics.view.IconicsImageButton
import com.mikepenz.iconics.view.IconicsImageView
import jp.wasabeef.recyclerview.animators.LandingAnimator
import kotlinx.coroutines.*
import kotlin.collections.ArrayList

class TitleListActivity : AppCompatActivity() {
    companion object {
        private const val CROSSFADE_DURATION = 500
        private const val ITEM_MARGIN = 25
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var imm: InputMethodManager

    private lateinit var actionBarView: ViewGroup
    private lateinit var loadingView: SpinKitView
    private lateinit var loadingText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: TitleEntryAdapter
    //TODO: IconicsImageButton?
    private lateinit var searchButton: IconicsImageButton

    private lateinit var titleStorage: TitleStorage

    private val titleData = ArrayList<TitleStorageEntry>()
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title_list)

        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        //sharedPreferences.edit().clear().commit()
        titleStorage = TitleStorage.load(sharedPreferences)
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        actionBarView = LayoutInflater.from(this).inflate(
            R.layout.action_bar_title_list,
            null, false
        ) as ViewGroup
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val layout = ActionBar.LayoutParams(
            ActionBar.LayoutParams.MATCH_PARENT,
            ActionBar.LayoutParams.MATCH_PARENT
        )
        supportActionBar?.setCustomView(actionBarView, layout)
        supportActionBar?.setDisplayShowCustomEnabled(true)

        searchButton = actionBarView.findViewById(R.id.button_search)
        searchButton.setOnClickListener {
            val searchIntent = Intent(this, SearchActivity::class.java)
            startActivity(searchIntent)
        }

        loadingView = findViewById(R.id.loading)
        loadingText = findViewById(R.id.loading_text)
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = LandingAnimator()
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
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
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = TitleEntryAdapter(titleData)
        recyclerView.adapter = adapter

        Dexter.withActivity(this)
            .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(object : BasePermissionListener() {
                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    super.onPermissionDenied(response)
                    Util.toast(this@TitleListActivity, "RIP")
                    finish()
                }
            })
            .check()

        job = coroutineScope.launch {
            ProviderFactory.init(this@TitleListActivity, sharedPreferences) {
                loadingText.text = it
            }
            titleData.clear()
            titleData.addAll(titleStorage.entryList)
            adapter.notifyItemRangeInserted(0, titleData.size)
            loadingView.visibility = View.GONE
            loadingText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            actionBarView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        titleStorage = TitleStorage.load(sharedPreferences)
        titleData.clear()
        titleData.addAll(titleStorage.entryList)
        adapter.notifyDataSetChanged()
    }

    private class TitleEntryViewHolder(
        val view: ViewGroup,
        val image: ImageView,
        val name: TextView,
        val details: TextView
    ) :
        RecyclerView.ViewHolder(view)

    private inner class TitleEntryAdapter private constructor() :
        RecyclerView.Adapter<TitleEntryViewHolder>() {
        private lateinit var data: List<TitleStorageEntry>

        constructor(data: List<TitleStorageEntry>) : this() {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitleEntryViewHolder {
            val titleView = LayoutInflater.from(parent.context).inflate(
                R.layout.list_title_info, parent,
                false
            ) as ViewGroup
            return TitleEntryViewHolder(
                titleView,
                titleView.findViewById(R.id.title_image),
                titleView.findViewById(R.id.title_name),
                titleView.findViewById(R.id.title_details)
            )
        }

        override fun onBindViewHolder(holder: TitleEntryViewHolder, pos: Int) {
            Glide
                .with(this@TitleListActivity)
                .load(data[pos].info.image)
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val title = data[pos].info.title
            holder.name.text = title
            holder.details.text = data[pos].provider
            holder.view.setOnClickListener {
                val episodesIntent = Intent(this@TitleListActivity, EpisodeListActivity::class.java)
                episodesIntent.putExtra(EpisodeListActivity.ARG, title)
                startActivity(episodesIntent)
            }
        }

        override fun getItemCount() = data.size
    }
}
