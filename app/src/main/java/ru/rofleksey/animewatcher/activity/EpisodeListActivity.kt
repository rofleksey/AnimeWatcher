package ru.rofleksey.animewatcher.activity

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.*
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.github.ybq.android.spinkit.SpinKitView
import com.google.android.material.snackbar.Snackbar
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.gpu.PixelationFilterTransformation
import jp.wasabeef.recyclerview.animators.LandingAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.database.EpisodeDownloadState
import ru.rofleksey.animewatcher.database.TitleStorage
import ru.rofleksey.animewatcher.database.TitleStorageEntry
import ru.rofleksey.animewatcher.util.AnimeUtils
import java.io.File

class EpisodeListActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EpisodeListActivity"
        private const val CROSSFADE_DURATION = 500
        private const val CROSSFADE_DURATION_BACKGROUND = 800
        private const val ITEM_MARGIN = 25
        private const val COLUMN_COUNT = 3
        const val ARG_TITLE = "titleName"
        const val ARG_PROVIDER = "titleProvider"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadManager: DownloadManager

    private lateinit var background: ImageView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: EpisodeAdapter

    private lateinit var titleEntry: TitleStorageEntry
    private lateinit var provider: AnimeProvider
    private lateinit var providerName: String
    private lateinit var titleStorage: TitleStorage
    private lateinit var argTitleName: String
    private lateinit var argTitleProvider: String

    private lateinit var downloadBroadcastReceiver: BroadcastReceiver

    private var lastEpisodeNumber: Int = -1
    private var snackbar: Snackbar? = null
    private val episodeData: ArrayList<EpisodeInfo> = ArrayList()
    private var refreshData: ArrayList<EpisodeInfo>? = null
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var shouldExecuteOnResume: Boolean = false

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episode_list)
        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        titleStorage = TitleStorage.load(sharedPreferences)

        argTitleName = intent.getStringExtra(ARG_TITLE) ?: ""
        argTitleProvider = intent.getStringExtra(ARG_PROVIDER) ?: ""
        titleEntry = titleStorage.findByName(argTitleName, argTitleProvider)
        providerName = titleEntry.provider
        provider = ProviderFactory.get(this, providerName)

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val barViewGroup = LayoutInflater.from(this).inflate(
            R.layout.action_bar_empty,
            null, false
        ) as ViewGroup
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val layout = ActionBar.LayoutParams(
            ActionBar.LayoutParams.MATCH_PARENT,
            ActionBar.LayoutParams.MATCH_PARENT
        )
        supportActionBar?.setCustomView(barViewGroup, layout)
        supportActionBar?.setDisplayShowCustomEnabled(true)

        background = findViewById(R.id.background)

        refreshLayout = findViewById(R.id.refresh_layout)
        refreshLayout.setOnRefreshListener {
            if (refreshData == null) {
                return@setOnRefreshListener
            }
            job?.cancel()
            job = coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                            override fun areItemsTheSame(
                                oldPos: Int,
                                newPos: Int
                            ): Boolean = episodeData[oldPos].name == refreshData!![newPos].name

                            override fun getOldListSize(): Int = episodeData.size

                            override fun getNewListSize(): Int = refreshData!!.size

                            override fun areContentsTheSame(
                                oldPos: Int,
                                newPos: Int
                            ): Boolean = episodeData[oldPos] == refreshData!![newPos]

                        })
                    }
                    episodeData.clear()
                    episodeData.addAll(refreshData!!)
                    titleEntry.cachedEpisodeList.clear()
                    titleEntry.cachedEpisodeList.addAll(refreshData!!)
                    titleStorage.save()
                    refreshData = null
                    result.dispatchUpdatesTo(adapter)
                    snackbar?.dismiss()
                    refreshLayout.isRefreshing = false
                    refreshLayout.isEnabled = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        refreshLayout.isEnabled = false

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = LandingAnimator()
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
//                val pos = parent.getChildAdapterPosition(view)
//                if (pos >= COLUMN_COUNT) {
//                    outRect.top = ITEM_MARGIN
//                }
//                if (pos < parent.adapter!!.itemCount - COLUMN_COUNT) {
//                    outRect.bottom = ITEM_MARGIN
//                }
                outRect.left =
                    ITEM_MARGIN
                outRect.right =
                    ITEM_MARGIN
                outRect.top =
                    ITEM_MARGIN
            }
        })
        layoutManager = GridLayoutManager(
            this,
            COLUMN_COUNT, RecyclerView.VERTICAL, false
        )
        recyclerView.layoutManager = layoutManager
        adapter = EpisodeAdapter(episodeData)
        recyclerView.adapter = adapter

        snackbar = Snackbar.make(refreshLayout, "Refreshing...", Snackbar.LENGTH_INDEFINITE)
            .also { it.show() }
        snackbar?.setBackgroundTint(resources.getColor(R.color.colorPanel))

        Glide
            .with(this)
            .load(provider.getGlideUrl(titleEntry.info.image ?: ""))
            .error(R.drawable.placeholder)
            .apply(bitmapTransform(BlurTransformation(15, 3)))
            .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION_BACKGROUND))
            .into(background)

        job = coroutineScope.launch {
            try {
                val firstTime = titleEntry.lastEpisodeNumber == -1
                val episodes = if (firstTime) {
                    withContext(Dispatchers.IO) {
                        provider.getAllEpisodes(titleEntry.info)
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        delay(250)
                        titleEntry.cachedEpisodeList
                    }
                }
                Log.v(TAG, "titleEntry.lastEpisodeNumber = ${titleEntry.lastEpisodeNumber}")
                episodeData.addAll(episodes)
                adapter.notifyItemRangeInserted(0, episodeData.size)
                if (firstTime) {
                    titleEntry.cachedEpisodeList.addAll(episodes)
                    titleEntry.lastEpisodeNumber = episodeData.size
                }
                lastEpisodeNumber = titleEntry.lastEpisodeNumber
                titleEntry.lastEpisodeNumber = episodes.size
                titleStorage.save()
                if (!isActive) {
                    return@launch
                }
                if (!firstTime) {
                    val newEpisodes = withContext(Dispatchers.IO) {
                        provider.getAllEpisodes(titleEntry.info)
                    }
                    if (!isActive) {
                        return@launch
                    }
                    val equals = withContext(Dispatchers.Default) {
                        newEpisodes == episodeData
                    }
                    if (!equals) {
                        refreshData = ArrayList(newEpisodes)
                        snackbar = Snackbar.make(
                            refreshLayout,
                            "Swipe down to refresh",
                            Snackbar.LENGTH_INDEFINITE
                        ).also { it.show() }
                        refreshLayout.isEnabled = true
                    } else {
                        snackbar?.dismiss()
                    }
                } else {
                    snackbar?.dismiss()
                }
                updateDownloadState()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {

            }
        }
    }

    private fun updateDownloadState(): Boolean {
        Log.v(TAG, "updateDownloadState()")
        var changedResult = false
        titleEntry.downloads.entries.forEach { entry ->
            var changed = false
            val query = DownloadManager.Query()
            query.setFilterById(entry.value.id)
            val result = downloadManager.query(query)
            if (result.moveToFirst()) {
                val status = result.getInt(result.getColumnIndex(DownloadManager.COLUMN_STATUS))
                when {
                    status == DownloadManager.STATUS_FAILED && entry.value.state != EpisodeDownloadState.REJECTED -> {
                        entry.value.state = EpisodeDownloadState.REJECTED
                        changedResult = true
                        changed = true
                    }
                    status == DownloadManager.STATUS_SUCCESSFUL && entry.value.state != EpisodeDownloadState.FINISHED -> {
                        entry.value.state = EpisodeDownloadState.FINISHED
                        changedResult = true
                        changed = true
                    }
                }
            } else if (entry.value.state != EpisodeDownloadState.REJECTED) {
                entry.value.state = EpisodeDownloadState.REJECTED
                changedResult = true
                changed = true
            }
            if (changed) {
                val index = episodeData.indexOfFirst { it.name == entry.key }
                Log.v(TAG, "Updating download state of episode #${entry.key}")
                adapter.notifyItemChanged(index)
            }
        }
        return changedResult
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (shouldExecuteOnResume) {
            titleStorage = TitleStorage.load(sharedPreferences)
            titleEntry = titleStorage.findByName(argTitleName, argTitleProvider)
            episodeData.clear()
            episodeData.addAll(titleEntry.cachedEpisodeList)
            adapter.notifyDataSetChanged()
        } else {
            shouldExecuteOnResume = true
        }
        if (updateDownloadState()) {
            titleStorage.save()
        }
        downloadBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.v(TAG, "Received broadcast event from DownloadManager")
                if (updateDownloadState()) {
                    titleStorage.save()
                }
            }
        }
        registerReceiver(
            downloadBroadcastReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(downloadBroadcastReceiver)
    }

    private class EpisodeViewHolder(
        val view: ViewGroup,
        val image: ImageView,
        val loading: SpinKitView,
        val text: TextView
    ) : RecyclerView.ViewHolder(view)

    private inner class EpisodeAdapter private constructor() :
        RecyclerView.Adapter<EpisodeViewHolder>() {
        private lateinit var data: List<EpisodeInfo>

        constructor(data: List<EpisodeInfo>) : this() {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
            val titleView = LayoutInflater.from(parent.context).inflate(
                R.layout.episode_info, parent,
                false
            ) as ViewGroup
            return EpisodeViewHolder(
                titleView,
                titleView.findViewById(R.id.episode_image),
                titleView.findViewById(R.id.episode_loading),
                titleView.findViewById(R.id.episode_text)
            )
        }

        override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
            val item = data[pos]
            Glide
                .with(this@EpisodeListActivity)
                .load(provider.getGlideUrl(item.image ?: titleEntry.info.image ?: ""))
                .placeholder(R.drawable.zero2)
                .error(R.drawable.placeholder)
                .run {
                    if (item.image == null) {
                        apply(bitmapTransform(PixelationFilterTransformation(25f)))
                    } else this
                }
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            holder.text.text = item.name
            if (titleEntry.downloads[item.name]?.state == EpisodeDownloadState.PENDING) {
                holder.loading.visibility = View.VISIBLE
            } else {
                holder.loading.visibility = View.GONE
            }
            holder.text.setBackgroundColor(
                ContextCompat.getColor(
                    this@EpisodeListActivity, when (titleEntry.downloads[item.name]?.state) {
                        null -> R.color.colorWhite
                        EpisodeDownloadState.REJECTED -> R.color.accent
                        EpisodeDownloadState.FINISHED -> R.color.primary
                        EpisodeDownloadState.PENDING -> R.color.colorHighlight
                    }
                )
            )
            holder.view.setOnClickListener {
                when (titleEntry.downloads[item.name]?.state) {
                    null, EpisodeDownloadState.REJECTED -> {
                        val downloadIntent =
                            Intent(this@EpisodeListActivity, DownloadActivity::class.java)
                        downloadIntent.putExtra(DownloadActivity.ARG_TITLE, titleEntry.info.title)
                        downloadIntent.putExtra(DownloadActivity.ARG_PROVIDER, titleEntry.provider)
                        downloadIntent.putExtra(DownloadActivity.ARG_EPISODE_NAME, item.name)
                        startActivity(downloadIntent)
                    }
                    EpisodeDownloadState.PENDING -> {
                        AnimeUtils.toast(this@EpisodeListActivity, "Already downloading!")
                    }
                    EpisodeDownloadState.FINISHED -> {
                        val downloadEntry = titleEntry.downloads[item.name]!!
                        if (!File(downloadEntry.file).exists()) {
                            AnimeUtils.toast(
                                this@EpisodeListActivity,
                                "File ${downloadEntry.file} doesn't exist!"
                            )
                            return@setOnClickListener
                        }
                        AnimeUtils.openInVlc(this@EpisodeListActivity, downloadEntry.file)
                    }
                }
            }
            holder.view.setOnLongClickListener {
                val downloadEntry =
                    titleEntry.downloads[item.name] ?: return@setOnLongClickListener false
                when (downloadEntry.state) {
                    EpisodeDownloadState.FINISHED -> {
                        val items = listOf("Open", "Make GIF", "Delete")
                        MaterialDialog(this@EpisodeListActivity).show {
                            listItems(items = items) { dialog, index, text ->
                                when (text) {
                                    "Open" -> {
                                        if (!File(downloadEntry.file).exists()) {
                                            AnimeUtils.toast(
                                                this@EpisodeListActivity,
                                                "File ${downloadEntry.file} doesn't exist!"
                                            )
                                            return@listItems
                                        }
                                        AnimeUtils.openDefaultFile(
                                            this@EpisodeListActivity,
                                            downloadEntry.file
                                        )
                                    }
                                    "Make GIF" -> {
                                        val gifIntent = Intent(
                                            this@EpisodeListActivity,
                                            GifSaveActivity::class.java
                                        )
                                        gifIntent.putExtra(
                                            GifSaveActivity.ARG_FILE,
                                            downloadEntry.file
                                        )
                                        startActivity(gifIntent)
                                    }
                                    "Delete" -> {
                                        MaterialDialog(this@EpisodeListActivity).show {
                                            title(text = "Delete file")
                                            message(text = "Remove downloaded file of episode #'${item.name}?'")
                                            positiveButton(text = "Yes") {
                                                downloadManager.remove(downloadEntry.id)
                                                File(downloadEntry.file).delete()
                                                titleEntry.downloads.remove(item.name)
                                                adapter.notifyItemChanged(holder.adapterPosition)
                                                titleStorage.save()
                                            }
                                            negativeButton(text = "No")
                                        }
                                    }
                                }
                            }
                            onDismiss {

                            }
                        }
                    }
                    EpisodeDownloadState.PENDING, EpisodeDownloadState.REJECTED -> {
                        MaterialDialog(this@EpisodeListActivity).show {
                            title(text = "Cancel download")
                            message(text = "Do you want to delete download entry of episode #'${item.name}?'")
                            positiveButton(text = "Yes") {
                                downloadManager.remove(downloadEntry.id)
                                File(downloadEntry.file).delete()
                                titleEntry.downloads.remove(item.name)
                                adapter.notifyItemChanged(holder.adapterPosition)
                                titleStorage.save()
                            }
                            negativeButton(text = "No")
                        }
                    }
                }
                true
            }
        }

        override fun getItemCount() = data.size
    }
}
