package ru.rofleksey.animewatcher

import android.Manifest
import android.app.DownloadManager
import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.google.android.material.snackbar.Snackbar
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.BasePermissionListener
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.recyclerview.animators.FlipInTopXAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.api.AnimeProvider
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.api.storage.StorageLocator
import ru.rofleksey.animewatcher.database.EpisodeDownloadState
import ru.rofleksey.animewatcher.database.EpisodeDownloadStatus
import ru.rofleksey.animewatcher.database.TitleStorage
import ru.rofleksey.animewatcher.database.TitleStorageEntry
import ru.rofleksey.animewatcher.util.Util
import ru.rofleksey.animewatcher.util.Util.Companion.toast
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DownloadActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DownloadActivity"
        private const val CROSSFADE_DURATION = 500
        private const val CROSSFADE_DURATION_BACKGROUND = 800
        private const val ITEM_MARGIN = 25
        const val ARG_TITLE = "titleName"
        const val ARG_PROVIDER = "titleProvider"
        const val ARG_EPISODE_NAME = "episodeName"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadManager: DownloadManager

    private lateinit var episodeName: String

    private lateinit var background: ImageView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: DownloadEntryAdapter

    private lateinit var titleEntry: TitleStorageEntry
    private lateinit var provider: AnimeProvider
    private lateinit var providerName: String
    private lateinit var titleStorage: TitleStorage

    private var startingDownload: Boolean = false

    private val downloadData: ArrayList<DownloadEntry> = ArrayList()

    private var snackbar: Snackbar? = null
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        sharedPreferences = getSharedPreferences("animewatcher", Context.MODE_PRIVATE)
        titleStorage = TitleStorage.load(sharedPreferences)

        val titleName = intent.getStringExtra(ARG_TITLE) ?: ""
        val titleProvider = intent.getStringExtra(ARG_PROVIDER) ?: ""
        episodeName = intent.getStringExtra(ARG_EPISODE_NAME) ?: ""
        titleEntry = titleStorage.findByName(titleName, titleProvider)
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
            job?.cancel()
            job = coroutineScope.launch {
                val size = downloadData.size
                downloadData.clear()
                adapter.notifyItemRangeRemoved(0, size)
                process()
            }
        }
        refreshLayout.isEnabled = true

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.itemAnimator = FlipInTopXAnimator()
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.top = ITEM_MARGIN
            }
        })
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = DownloadEntryAdapter(downloadData)
        recyclerView.adapter = adapter

        Glide
            .with(this)
            .load(provider.getGlideUrl(titleEntry.info.image ?: ""))
            .error(R.drawable.placeholder)
            .apply(bitmapTransform(BlurTransformation(15, 3)))
            .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION_BACKGROUND))
            .into(background)

        job = coroutineScope.launch {
            process()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    enum class EntryType {
        LINK_WITH_STORAGE, PROCESSING, READY, ERROR, INVALID_STORAGE
    }

    data class DownloadEntry(
        val name: String,
        val image: Int,
        val entryId: Int,
        var details: String,
        var entryType: EntryType,
        val storageResult: StorageResult?
    )

    private suspend fun process() {
        try {
            refreshLayout.isRefreshing = true
            snackbar?.dismiss()
            val episodes = titleEntry.cachedEpisodeList
            val links = withContext(Dispatchers.IO) {
                provider.getStorageLinks(
                    titleEntry.info,
                    episodes.first { it.name == episodeName }
                )
            }
            if (links.isEmpty()) {
                throw Exception("No links were retrieved from $provider")
            }
            suspendCoroutine<Unit> { cont ->
                Dexter.withActivity(this@DownloadActivity)
                    .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(object : BasePermissionListener() {
                        override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                            super.onPermissionGranted(response)
                            cont.resume(Unit)
                        }

                        override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                            super.onPermissionDenied(response)
                            toast(this@DownloadActivity, "Permission denied")
                            cont.resumeWithException(Exception("Permission denied"))
                        }
                    })
                    .check()
            }
            val linksAndStorages = links.map { providerResult ->
                Pair(providerResult, StorageLocator.locate(providerResult.link))
            }.sortedWith(
                compareBy(
                    { if (it.second == null) 1 else 0 },
                    { if (it.second == null) 1337 else -it.second!!.score },
                    { -it.first.quality.num }
                )
            )
            if (linksAndStorages.isEmpty()) {
                throw Exception("Failed to determine download method")
            }
            linksAndStorages.forEachIndexed { index, pair ->
                if (pair.second == null) {
                    downloadData.add(
                        DownloadEntry(
                            "Unsupported storage",
                            R.drawable.placeholder,
                            index,
                            pair.first.link,
                            EntryType.INVALID_STORAGE,
                            null
                        )
                    )
                } else {
                    downloadData.add(
                        DownloadEntry(
                            "(${Util.qualityToStr(pair.first.quality)}) - ${pair.second!!.name}",
                            R.drawable.zero2,
                            index,
                            "getting links...",
                            EntryType.LINK_WITH_STORAGE,
                            null
                        )
                    )
                }
            }
            adapter.notifyItemRangeInserted(0, downloadData.size)
            linksAndStorages.forEachIndexed { index, pair ->
                val storage = pair.second
                if (storage != null) {
                    val providerResult = pair.first
                    Log.v(
                        TAG,
                        "Processing ${storage.name} (${providerResult.quality}): ${providerResult.link}"
                    )
                    val curIndex = downloadData.indexOfFirst {
                        it.entryId == index
                    }
                    try {
                        downloadData[curIndex].entryType = EntryType.PROCESSING
                        downloadData[curIndex].details = "processing..."
                        adapter.notifyItemChanged(curIndex)

                        val result = withContext(Dispatchers.IO) {
                            storage.extract(providerResult)
                        }.sortedBy {
                            -it.quality.num
                        }.map { result ->
                            Log.v(TAG, "storageLink (${result.quality}) - ${result.link}")
                            DownloadEntry(
                                "(${Util.qualityToStr(result.quality)}) - ${pair.second!!.name}",
                                R.drawable.zero2,
                                index,
                                "ready",
                                EntryType.READY,
                                result
                            )
                        }
                        downloadData.removeAt(curIndex)
                        adapter.notifyItemRemoved(curIndex)
                        downloadData.addAll(curIndex, result)
                        adapter.notifyItemRangeInserted(curIndex, result.size)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        downloadData[curIndex].entryType = EntryType.ERROR
                        downloadData[curIndex].details = e.message ?: "ERROR"
                        adapter.notifyItemChanged(curIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            snackbar =
                Snackbar.make(refreshLayout, e.message ?: "ERROR", Snackbar.LENGTH_INDEFINITE)
                    .also { it.show() }
            snackbar?.setBackgroundTint(ContextCompat.getColor(this, R.color.colorPanel))
        } finally {
            refreshLayout.isRefreshing = false
        }
    }

    private fun download(episodeName: String, result: StorageResult) {
        val downloadTitle = "${titleEntry.info.title}_${episodeName}.mp4"
        val fileName = Util.sanitizeForFileName(downloadTitle)
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
        downloadRequest.allowScanningByMediaScanner()
        downloadRequest.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        val taskId = downloadManager.enqueue(downloadRequest)
        titleEntry.downloads[episodeName] = EpisodeDownloadStatus(
            taskId,
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            ).toString(),
            EpisodeDownloadState.PENDING
        )
        titleStorage.save()
        toast(this, description)
    }

    private class DownloadEntryHolder(
        val view: ViewGroup,
        val image: ImageView,
        val name: TextView,
        val details: TextView
    ) : RecyclerView.ViewHolder(view)

    private inner class DownloadEntryAdapter private constructor() :
        RecyclerView.Adapter<DownloadEntryHolder>() {
        private lateinit var data: List<DownloadEntry>

        constructor(data: List<DownloadEntry>) : this() {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadEntryHolder {
            val titleView = LayoutInflater.from(parent.context).inflate(
                R.layout.download_info, parent,
                false
            ) as ViewGroup
            return DownloadEntryHolder(
                titleView,
                titleView.findViewById(R.id.download_image),
                titleView.findViewById(R.id.download_name),
                titleView.findViewById(R.id.download_details)
            )
        }

        override fun onBindViewHolder(holder: DownloadEntryHolder, pos: Int) {
            val item = data[pos]
            Glide
                .with(this@DownloadActivity)
                .load(item.image)
                .placeholder(R.drawable.zero2)
                .error(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            holder.name.text = item.name
            holder.details.text = item.details
            holder.view.setBackgroundColor(
                ContextCompat.getColor(
                    this@DownloadActivity, when (item.entryType) {
                        EntryType.LINK_WITH_STORAGE -> R.color.colorPanel
                        EntryType.PROCESSING -> R.color.colorHighlight
                        EntryType.ERROR -> R.color.accent
                        EntryType.READY -> R.color.primary
                        EntryType.INVALID_STORAGE -> R.color.colorBlack
                    }
                )
            )
            holder.view.setOnClickListener {
                if (item.storageResult != null) {
                    if (startingDownload) {
                        return@setOnClickListener
                    }
                    startingDownload = true
                    download(episodeName, item.storageResult)
                    title
                    finish()
                }
            }
            holder.view.setOnLongClickListener {
                if (item.storageResult != null) {
                    Util.openDefault(this@DownloadActivity, item.storageResult.link)
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
        }

        override fun getItemCount() = data.size
    }
}
