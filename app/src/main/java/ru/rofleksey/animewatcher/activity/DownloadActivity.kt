package ru.rofleksey.animewatcher.activity

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
import android.view.animation.OvershootInterpolator
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
import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.api.model.ProviderResult
import ru.rofleksey.animewatcher.api.model.StorageResult
import ru.rofleksey.animewatcher.api.provider.AnimeProvider
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.api.storage.Storage
import ru.rofleksey.animewatcher.api.storage.StorageLocator
import ru.rofleksey.animewatcher.database.EpisodeDownloadState
import ru.rofleksey.animewatcher.database.EpisodeDownloadStatus
import ru.rofleksey.animewatcher.database.TitleStorage
import ru.rofleksey.animewatcher.database.TitleStorageEntry
import ru.rofleksey.animewatcher.util.AnimeUtils
import ru.rofleksey.animewatcher.util.AnimeUtils.Companion.toast
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
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
        recyclerView.itemAnimator = SlideInLeftAnimator().apply {
            setInterpolator(OvershootInterpolator(1f))
        }
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.top =
                    ITEM_MARGIN
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
        REDIRECT, LINK_WITH_STORAGE, PROCESSING, READY, ERROR, INVALID_STORAGE
    }

    data class DownloadEntry(
        val name: String,
        val image: Int,
        var entryId: Int,
        var details: String,
        var entryType: EntryType,
        val sourceLink: String,
        val storageResult: StorageResult?
    )

    data class QueueEntry(
        val providerResult: ProviderResult,
        val storage: Storage?,
        val id: Int
    )

    class IdHolder {
        var maxId = 0

        fun next(): Int {
            return maxId++
        }
    }

    private fun queueEntryToDownloadEntry(
        queueEntry: QueueEntry,
        isRedirect: Boolean
    ): DownloadEntry {
        return if (queueEntry.storage == null) {
            DownloadEntry(
                "Unsupported storage",
                R.drawable.placeholder,
                queueEntry.id,
                queueEntry.providerResult.link,
                EntryType.INVALID_STORAGE,
                queueEntry.providerResult.link,
                null
            )
        } else {
            if (!isRedirect) {
                DownloadEntry(
                    "(${AnimeUtils.qualityToStr(queueEntry.providerResult.quality)}) - ${queueEntry.storage.name}",
                    R.drawable.zero2,
                    queueEntry.id,
                    "getting links...",
                    EntryType.LINK_WITH_STORAGE,
                    queueEntry.providerResult.link,
                    null
                )
            } else {
                DownloadEntry(
                    "(${AnimeUtils.qualityToStr(queueEntry.providerResult.quality)}) - ${queueEntry.storage.name}",
                    R.drawable.zero2,
                    queueEntry.id,
                    "following redirect...",
                    EntryType.REDIRECT,
                    queueEntry.providerResult.link,
                    null
                )
            }
        }
    }

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
                throw Exception("No links were retrieved from $providerName")
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
            val idHolder = IdHolder()

            val comparator: Comparator<QueueEntry> = compareBy(
                { if (it.storage == null) 1 else 0 },
                { if (it.storage == null) 1337 else -it.storage.score },
                { -it.providerResult.quality.num }
            )

            val linksAndStorages = links.map { providerResult ->
                QueueEntry(
                    providerResult,
                    StorageLocator.locate(providerResult.link),
                    idHolder.next()
                )
            }.sortedWith(comparator)



            if (linksAndStorages.isEmpty()) {
                throw Exception("Failed to determine download method")
            }
            linksAndStorages.forEach { queueEntry ->
                downloadData.add(queueEntryToDownloadEntry(queueEntry, false))
            }
            adapter.notifyItemRangeInserted(0, downloadData.size)
            val queue = LinkedList<QueueEntry>()
            queue.addAll(linksAndStorages)
            while (queue.isNotEmpty()) {
                val queueEntry = queue.removeFirst()
                val listIndex = downloadData.indexOfFirst {
                    it.entryId == queueEntry.id
                }
                val storage = queueEntry.storage
                if (storage != null) {
                    val providerResult = queueEntry.providerResult
                    Log.v(
                        TAG,
                        "Processing ${storage.name} (${providerResult.quality}): ${providerResult.link}"
                    )
                    try {
                        downloadData[listIndex].entryType =
                            EntryType.PROCESSING
                        downloadData[listIndex].details = "processing..."
                        adapter.notifyItemChanged(listIndex)

                        val result = withContext(Dispatchers.IO) {
                            storage.extract(providerResult)
                        }.sortedBy {
                            -it.quality.num
                        }.flatMap { result ->
                            Log.v(TAG, "storageLink (${result.quality}) - ${result.link}")
                            if (result.isRedirect) {
                                val alreadyHasLink = downloadData.any { existingEntry ->
                                    existingEntry.sourceLink == result.link
                                }
                                if (!alreadyHasLink) {
                                    val newEntry = QueueEntry(
                                        ProviderResult(
                                            result.link,
                                            result.quality
                                        ),
                                        StorageLocator.locate(result.link),
                                        idHolder.next()
                                    )
                                    queue.addFirst(newEntry)
                                    listOf(queueEntryToDownloadEntry(newEntry, true))
                                } else {
                                    listOf()
                                }
                            } else {
                                listOf(
                                    DownloadEntry(
                                        "(${AnimeUtils.qualityToStr(result.quality)}) - ${storage.name}",
                                        R.drawable.zero2,
                                        queueEntry.id,
                                        "ready",
                                        EntryType.READY,
                                        result.link,
                                        result
                                    )
                                )
                            }
                        }
                        downloadData.removeAt(listIndex)
                        adapter.notifyItemRemoved(listIndex)
                        downloadData.addAll(listIndex, result)
                        adapter.notifyItemRangeInserted(listIndex, result.size)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        downloadData[listIndex].entryType =
                            EntryType.ERROR
                        downloadData[listIndex].details = e.message ?: "ERROR"
                        adapter.notifyItemChanged(listIndex)
                    }
                    delay(250)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            snackbar =
                Snackbar.make(refreshLayout, e.message ?: "ERROR", Snackbar.LENGTH_INDEFINITE)
                    .also { it.show() }
            snackbar?.setBackgroundTint(
                ContextCompat.getColor(
                    this,
                    R.color.colorPanel
                )
            )
        } finally {
            refreshLayout.isRefreshing = false
        }
    }

    private fun download(episodeName: String, result: StorageResult) {
        val downloadTitle = "${titleEntry.info.title}_${episodeName}.mp4"
        val fileName = AnimeUtils.sanitizeForFileName(downloadTitle)
        val downloadRequest = DownloadManager.Request(result.link.toUri())
        val description = "Downloading episode #$episodeName of ${titleEntry.info.title}"
        for (entry in result.headers.entries) {
            downloadRequest.addRequestHeader(entry.key, entry.value)
            Log.v(TAG, "Setting header ${entry.key}=${entry.value}")
        }
        downloadRequest.addRequestHeader("User-Agent", AnimeUtils.USER_AGENT)
        downloadRequest.setTitle(downloadTitle)
        downloadRequest.setDescription(description)
        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downloadRequest.setVisibleInDownloadsUi(true)
        downloadRequest.allowScanningByMediaScanner()
        downloadRequest.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MOVIES,
            fileName
        )
        val taskId = downloadManager.enqueue(downloadRequest)
        titleEntry.downloads[episodeName] = EpisodeDownloadStatus(
            taskId,
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
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
                        EntryType.REDIRECT -> R.color.colorOrange
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
                    AnimeUtils.openDefault(this@DownloadActivity, item.storageResult.link)
                    return@setOnLongClickListener true
                }
                return@setOnLongClickListener false
            }
        }

        override fun getItemCount() = data.size
    }
}
