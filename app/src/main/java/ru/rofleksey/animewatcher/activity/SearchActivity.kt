package ru.rofleksey.animewatcher.activity

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.afollestad.materialdialogs.list.listItems
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.github.ybq.android.spinkit.SpinKitView
import com.mikepenz.iconics.view.IconicsImageButton
import jp.wasabeef.recyclerview.animators.FadeInAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.api.model.TitleInfo
import ru.rofleksey.animewatcher.api.provider.AnimeProvider
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.database.TitleStorage
import ru.rofleksey.animewatcher.database.TitleStorageEntry
import ru.rofleksey.animewatcher.util.AnimeUtils
import ru.rofleksey.animewatcher.util.AnimeUtils.Companion.toast
import ru.rofleksey.animewatcher.util.Debounce
import java.io.File
import kotlin.system.measureTimeMillis


class SearchActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SearchActivity"
        private const val CROSSFADE_DURATION = 500
        private const val SEARCH_DELAY = 350L
        private const val ITEM_MARGIN = 30
        private const val SEARCH_HINT = "Search on"
        private const val TAPS_TILL_SECRET = 5
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var imm: InputMethodManager
    private lateinit var downloadManager: DownloadManager


    private lateinit var loadingView: SpinKitView
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: TitleAdapter
    private lateinit var searchView: EditText
    private lateinit var buttonBack: IconicsImageButton
    private lateinit var buttonProviders: IconicsImageButton
    private lateinit var emptyBackground: LottieAnimationView
    private lateinit var emptyText: TextView

    private var countTillSecretActivity =
        TAPS_TILL_SECRET

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

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        val storageMeasure = measureTimeMillis {
            titleStorage = TitleStorage.load(sharedPreferences)
        }
        Log.v(TAG, "titleStorage loaded in $storageMeasure ms")
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        providerName = sharedPreferences.getString("provider", ProviderFactory.DEFAULT)
            ?: ProviderFactory.ANIMEPAHE
        provider = ProviderFactory.get(this, providerName)

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
        searchView.hint = "$SEARCH_HINT $providerName"

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
                if (s == null || s.isEmpty()) {
                    titleData.clear()
                    adapter.notifyDataSetChanged()
                    toggleBackground(true)
                    return
                }
                searchDebounces.attempt(Runnable {
                    job = runSearch(s.toString())
                })
            }

        })
        buttonProviders.setOnClickListener {
            MaterialDialog(this@SearchActivity).show {
                title(text = "Select provider")
                listItems(R.array.providers) { dialog, index, text ->
                    searchDebounces.stop()
                    job?.cancel()
                    when (index) {
                        0 -> {
                            providerName = ProviderFactory.ANIMEPAHE
                            provider = ProviderFactory.get(this@SearchActivity, providerName)
                            toast(this@SearchActivity, "Using AnimePahe")
                        }
                        1 -> {
                            providerName = ProviderFactory.ANIMEDUB
                            provider = ProviderFactory.get(this@SearchActivity, providerName)
                            toast(this@SearchActivity, "Using AnimeDub")
                        }
                        2 -> {
                            providerName = ProviderFactory.GOGOANIME
                            provider = ProviderFactory.get(this@SearchActivity, providerName)
                            toast(this@SearchActivity, "Using GogoAnime")
                        }
                        3 -> {
                            providerName = ProviderFactory.KICKASSANIME
                            provider = ProviderFactory.get(this@SearchActivity, providerName)
                            toast(this@SearchActivity, "Using KickassAnime")
                        }
                        else -> {
                            toast(this@SearchActivity, "ERROR: INVALID PROVIDER")
                            return@listItems
                        }
                    }
                    titleData.clear()
                    adapter.notifyDataSetChanged()
                    sharedPreferences.edit().putString("provider", providerName).apply()
                    searchView.hint = "$SEARCH_HINT $providerName"
                    searchDebounces.attempt(Runnable {
                        job = runSearch(searchView.text.toString())
                    })
                    dialog.dismiss()
                }
                negativeButton(text = "No")
            }
        }

        emptyBackground = findViewById(R.id.empty_background)
        emptyText = findViewById(R.id.empty_text)
        loadingView = findViewById(R.id.loading)

        emptyBackground.setOnClickListener {
            AnimeUtils.vibrate(this, 20)
            countTillSecretActivity -= 1
            YoYo
                .with(Techniques.Shake)
                .duration(150)
                .playOn(emptyBackground)
            if (countTillSecretActivity == 0) {
                countTillSecretActivity =
                    TAPS_TILL_SECRET
                val searchIntent = Intent(this, SecretActivity::class.java)
                startActivity(searchIntent)
            }
        }

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
                outRect.top =
                    ITEM_MARGIN
            }
        })
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = TitleAdapter(titleData)
        recyclerView.adapter = adapter
        toggleBackground(true)
    }

    private fun toggleBackground(on: Boolean) {
        if (on && titleData.isEmpty()) {
            emptyBackground.visibility = View.VISIBLE
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyBackground.visibility = View.GONE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        searchDebounces.stop()
        job?.cancel()
        super.onDestroy()
    }

    fun runSearch(s: String): Job {
        return coroutineScope.launch {
            try {
                toggleBackground(false)
                loadingView.visibility = View.VISIBLE
                val results = withContext(Dispatchers.IO) {
                    provider.search(s)
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
                toggleBackground(true)
            }
        }
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
                .placeholder(R.drawable.zero2)
                .error(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val title = item.title
            val isFavourite = titleStorage.hasTitle(item.title, providerName)
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
                    val episodesIntent =
                        Intent(this@SearchActivity, EpisodeListActivity::class.java)
                    episodesIntent.putExtra(EpisodeListActivity.ARG_TITLE, title)
                    episodesIntent.putExtra(EpisodeListActivity.ARG_PROVIDER, providerName)
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
                                val entry = titleStorage.findByName(title, providerName)
                                entry.downloads.values.forEach { downloadItem ->
                                    downloadManager.remove(downloadItem.id)
                                    File(downloadItem.file).delete()
                                }
                                it.remove(entry)
                            }
                            toast(this@SearchActivity, "'$title' removed")
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
