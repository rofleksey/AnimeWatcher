package ru.rofleksey.animewatcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.github.ybq.android.spinkit.SpinKitView
import com.mikepenz.iconics.view.IconicsImageButton
import jp.wasabeef.recyclerview.animators.ScaleInAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.storage.TitleStorage
import ru.rofleksey.animewatcher.storage.TitleStorageEntry
import ru.rofleksey.animewatcher.util.Util


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
        val providerName = sharedPreferences.getString("provider", ProviderFactory.DEFAULT)
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
            //searchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(searchIntent)
        }

        loadingView = findViewById(R.id.loading)
        loadingText = findViewById(R.id.loading_text)
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = ScaleInAnimator()
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
        adapter = TitleEntryAdapter(titleData)
        recyclerView.adapter = adapter

        job = coroutineScope.launch {
            if (providerName == ProviderFactory.ANIMEPAHE ||
                titleStorage.entryList.any { it.provider == ProviderFactory.ANIMEPAHE }
            ) {
                ProviderFactory.init(this@TitleListActivity, sharedPreferences) {
                    loadingText.text = it
                }
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
            val item = data[pos]
            Glide
                .with(this@TitleListActivity)
                .load(item.info.image)
                .placeholder(R.drawable.zero2)
                .error(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            holder.name.text = item.info.title
            holder.details.text = item.provider
            holder.view.setOnClickListener {
                val episodesIntent = Intent(this@TitleListActivity, EpisodeListActivity::class.java)
                episodesIntent.putExtra(EpisodeListActivity.ARG_TITLE, item.info.title)
                episodesIntent.putExtra(EpisodeListActivity.ARG_PROVIDER, item.provider)
                startActivity(episodesIntent)
            }
            holder.view.setOnLongClickListener {
                MaterialDialog(this@TitleListActivity).show {
                    title(text = "Remove")
                    message(text = "Remove '${item.info.title}' from your watching list?")
                    positiveButton(text = "Yes") {
                        titleStorage.update {
                            it.remove(item)
                        }
                        titleData.remove(item)
                        Util.toast(this@TitleListActivity, "'${item.info.title}' removed")
                        adapter.notifyItemRemoved(holder.adapterPosition)
                    }
                    negativeButton(text = "No")
                }
                true
            }
        }

        override fun getItemCount() = data.size
    }
}
