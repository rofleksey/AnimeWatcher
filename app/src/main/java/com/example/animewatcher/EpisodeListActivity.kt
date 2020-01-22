package com.example.animewatcher

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.example.animewatcher.api.AnimeProvider
import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.provider.ProviderFactory
import com.example.animewatcher.api.storage.StorageLocator
import com.example.animewatcher.storage.TitleStorage
import com.example.animewatcher.storage.TitleStorageEntry
import com.example.animewatcher.util.Util.Companion.openInVlc
import com.example.animewatcher.util.Util.Companion.sanitizeForFileName
import jp.wasabeef.recyclerview.animators.LandingAnimator
import kotlinx.coroutines.*
import java.lang.Exception
import kotlin.collections.ArrayList
import com.example.animewatcher.util.Util.Companion.toast
import com.github.ybq.android.spinkit.SpinKitView
import com.google.android.material.snackbar.Snackbar
import jp.wasabeef.glide.transformations.BlurTransformation

class EpisodeListActivity : AppCompatActivity() {
    companion object {
        private const val CROSSFADE_DURATION = 500
        private const val CROSSFADE_DURATION_BACKGROUND = 800
        private const val ITEM_MARGIN = 25
        private const val COLUMN_COUNT = 3
        const val ARG = "titleName"
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

    private var lastEpisodeNumber: Int = -1
    private var snackbar: Snackbar? = null
    private val episodeData: ArrayList<EpisodeInfo> = ArrayList()
    private var refreshData: ArrayList<EpisodeInfo>? = null
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episode_list)
        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        titleStorage = TitleStorage.load(sharedPreferences)

        val titleName = intent.getStringExtra(ARG) ?: ""
        titleEntry = titleStorage.findByName(titleName)
        providerName = titleEntry.provider
        provider = ProviderFactory.get(providerName)

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val barViewGroup = LayoutInflater.from(this).inflate(
            R.layout.action_bar_episode_list,
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
                outRect.left = ITEM_MARGIN
                outRect.right = ITEM_MARGIN
                outRect.top = ITEM_MARGIN
            }
        })
        layoutManager = GridLayoutManager(this, COLUMN_COUNT, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        adapter = EpisodeAdapter(episodeData)
        recyclerView.adapter = adapter

        snackbar = Snackbar.make(refreshLayout, "Refreshing...", Snackbar.LENGTH_INDEFINITE).also { it.show() }
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
                    titleEntry.cachedEpisodeList
                }
                println("titleEntry.lastEpisodeNumber = ${titleEntry.lastEpisodeNumber}")
                episodeData.addAll(episodes)
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
                adapter.notifyItemRangeInserted(0, episodeData.size)
                if (!firstTime) {
                    provider.clearCache()
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
                        snackbar = Snackbar.make(refreshLayout, "Swipe down to refresh", Snackbar.LENGTH_INDEFINITE).also { it.show() }
                        refreshLayout.isEnabled = true
                    } else {
                        snackbar?.dismiss()
                    }
                } else {
                    snackbar?.dismiss()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {

            }
        }
    }

    private fun presentLinkToTheUser(episodeName: String, link: String) {
        MaterialDialog(this).show {
            title(text = "Action")
            message(text = "What do you want to do with #$episodeName ?")
            positiveButton(text = "watch via VLC") {
                openInVlc(this@EpisodeListActivity, link)
            }
            negativeButton(text = "download") {
                val downloadRequest = DownloadManager.Request(link.toUri())
                val downloadTitle = "${titleEntry.info.title}_${episodeName}.mp4"
                val description = "Downloading episode #$episodeName of ${titleEntry.info.title}"
                downloadRequest.setTitle(downloadTitle)
                downloadRequest.setDescription(description)
                downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                downloadRequest.setVisibleInDownloadsUi(true)
                downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizeForFileName(downloadTitle))
                val taskId = downloadManager.enqueue(downloadRequest)
//                titleEntry.downloadTasks[number] = taskId
//                titleStorage.save()
                toast(context, description)
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
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
                .load(provider.getGlideUrl(item.image ?: ""))
                .placeholder(R.drawable.img)
                .error(R.drawable.placeholder)
                //.apply(RequestOptions.circleCropTransform())
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val episodeName = item.name
            holder.text.text = episodeName
            holder.view.setOnClickListener {
                if (holder.loading.visibility != View.GONE || job?.isActive != false) {
                    return@setOnClickListener
                }
                job = coroutineScope.launch {
                    try {
                        holder.loading.visibility = View.VISIBLE
                        val links = withContext(Dispatchers.IO) {
                            provider.getStorageLinks(
                                titleEntry.info,
                                item
                            )
                        }
                        val link = links[Quality.q720]
                        if (link != null) {
                            val storage = StorageLocator.locate(link)
                            if (storage != null) {
                                val streamLink = withContext(Dispatchers.IO) { storage.extractDownload(link) }
                                println(streamLink)
                                presentLinkToTheUser(episodeName, streamLink)
                            } else {
                                toast(
                                    this@EpisodeListActivity,
                                    "Can't determine storage: $link"
                                )
                            }
                        } else {
                            toast(this@EpisodeListActivity, "Can't find 720p link: $links")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        holder.loading.visibility = View.GONE
                    }
                }
            }
        }

        override fun getItemCount() = data.size
    }
}
