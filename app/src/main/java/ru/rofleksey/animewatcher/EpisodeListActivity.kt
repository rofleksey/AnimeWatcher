package ru.rofleksey.animewatcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
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
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.gpu.PixelationFilterTransformation
import jp.wasabeef.recyclerview.animators.LandingAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.EpisodeInfo
import ru.rofleksey.animewatcher.api.model.Quality
import ru.rofleksey.animewatcher.api.model.StorageAction
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.api.storage.StorageLocator
import ru.rofleksey.animewatcher.storage.TitleStorage
import ru.rofleksey.animewatcher.storage.TitleStorageEntry
import ru.rofleksey.animewatcher.util.Util.Companion.openInVlc
import ru.rofleksey.animewatcher.util.Util.Companion.sanitizeForFileName
import ru.rofleksey.animewatcher.util.Util.Companion.toast
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

        val titleName = intent.getStringExtra(ARG_TITLE) ?: ""
        val titleProvider = intent.getStringExtra(ARG_PROVIDER) ?: ""
        titleEntry = titleStorage.findByName(titleName, titleProvider)
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
                    titleEntry.cachedEpisodeList
                }
                Log.v(TAG, "titleEntry.lastEpisodeNumber = ${titleEntry.lastEpisodeNumber}")
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {

            }
        }
    }

    private fun download(episodeName: String, result: StorageResult) {
        val downloadTitle = "${titleEntry.info.title}_${episodeName}.mp4"
        val fileName = sanitizeForFileName(downloadTitle)
        val downloadRequest = DownloadManager.Request(result.link.toUri())
        val description = "Downloading episode #$episodeName of ${titleEntry.info.title}"
        for (entry in result.headers.entries) {
            downloadRequest.addRequestHeader(entry.key, entry.value)
            Log.v(TAG, "Setting header ${entry.key}=${entry.value}")
        }
        downloadRequest.setTitle(downloadTitle)
        downloadRequest.setDescription(description)
        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downloadRequest.setVisibleInDownloadsUi(true)
        downloadRequest.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        val taskId = downloadManager.enqueue(downloadRequest)
        toast(this, description)
    }

    private fun openDefault(link: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(browserIntent)
    }

    private fun presentLinkToTheUser(episodeName: String, result: StorageResult) {
        when (result.action) {
            StorageAction.DOWNLOAD_ONLY -> download(episodeName, result)
            StorageAction.CUSTOM_ONLY -> openDefault(result.link)
            else -> MaterialDialog(this).show {
                title(text = "Action")
                message(text = "What do you want to do with #$episodeName ?")
                positiveButton(text = "download") {
                    download(episodeName, result)
                }
                neutralButton(text = "open in VLC") {
                    openInVlc(this@EpisodeListActivity, result.link)
                }
                negativeButton(text = "custom") {
                    openDefault(result.link)
                }
            }
        }

    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private suspend fun process(episodeInfo: EpisodeInfo, askStorage: Boolean) {
        val links = withContext(Dispatchers.IO) {
            provider.getStorageLinks(
                titleEntry.info,
                episodeInfo,
                Quality.q720
            )
        }
        if (links.isEmpty()) {
            throw Exception("No links retreived from $provider")
        }
        suspendCoroutine<Unit> { cont ->
            Dexter.withActivity(this@EpisodeListActivity)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(object : BasePermissionListener() {
                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                        super.onPermissionGranted(response)
                        cont.resume(Unit)
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                        super.onPermissionDenied(response)
                        toast(this@EpisodeListActivity, "Permission denied")
                        cont.resumeWithException(Exception("Permission denied"))
                    }
                })
                .check()
        }
        val linksAndStorages = links.map {
            Pair(it, StorageLocator.locate(it))
        }.filter {
            it.second != null
        }.sortedBy {
            -it.second!!.score
        }
        if (linksAndStorages.isEmpty()) {
            throw Exception("Failed to determine download method")
        }
        val index = if (!askStorage) {
            0
        } else {
            val displayList = linksAndStorages.map { it.second!!.name }
            var resumed = false
            val index = suspendCoroutine<Int> { cont ->
                MaterialDialog(this).show {
                    listItems(items = displayList) { dialog, index, text ->
                        resumed = true
                        cont.resume(index)
                        dialog.dismiss()
                    }
                    onDismiss {
                        if (!resumed) {
                            cont.resumeWithException(Exception("Dialog closed"))
                        }
                    }
                }
            }
            index
        }
        val storage = linksAndStorages[index].second!!
        val finalLink = linksAndStorages[index].first
        Log.v(TAG, "Using ${storage.name}: $finalLink")
        val storageResult = withContext(Dispatchers.IO) {
            storage.extract(finalLink, Quality.q720)
        }
        Log.v(TAG, "storageLink - ${storageResult.link}")
        presentLinkToTheUser(episodeInfo.name, storageResult)
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

        private fun startJob(holder: EpisodeViewHolder, info: EpisodeInfo, askStorage: Boolean) {
            job = coroutineScope.launch {
                try {
                    holder.loading.visibility = View.VISIBLE
                    process(info, askStorage)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(refreshLayout, e.message ?: "Error", Snackbar.LENGTH_SHORT).show()
                } finally {
                    holder.loading.visibility = View.GONE
                }
            }
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
            holder.view.setOnClickListener {
                if (holder.loading.visibility != View.GONE || job?.isActive != false) {
                    return@setOnClickListener
                }
                startJob(holder, item, false)
            }
            holder.view.setOnLongClickListener {
                if (holder.loading.visibility != View.GONE || job?.isActive != false) {
                    return@setOnLongClickListener true
                }
                startJob(holder, item, true)
                true
            }
        }

        override fun getItemCount() = data.size
    }
}
