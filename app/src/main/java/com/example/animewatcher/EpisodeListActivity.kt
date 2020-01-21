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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
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
import java.util.*
import kotlin.collections.ArrayList
import com.example.animewatcher.util.Util.Companion.toast
import com.github.ybq.android.spinkit.SpinKitView

class EpisodeListActivity : AppCompatActivity() {
    companion object {
        private val CROSSFADE_DURATION = 500
        private val ITEM_MARGIN = 25
        val ARG = "titleName"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadManager: DownloadManager

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: EpisodeAdapter

    private lateinit var titleEntry: TitleStorageEntry
    private lateinit var provider: AnimeProvider
    private lateinit var providerName: String
    private lateinit var titleStorage: TitleStorage
    private val episodeData = ArrayList<EpisodeInfo>()
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
                if (pos >= 1) {
                    outRect.top = ITEM_MARGIN
                }
                if (pos < parent.childCount - 1) {
                    outRect.bottom = ITEM_MARGIN
                }
            }
        })
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = EpisodeAdapter(episodeData)
        recyclerView.adapter = adapter

        job = coroutineScope.launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    provider.getAllEpisodes(titleEntry.info.title)
                }
                episodeData.clear()
                episodeData.addAll(episodes)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun presentLinkToTheUser(number: Int, link: String) {
        MaterialDialog(this).show {
            title(text = "Action")
            message(text = "What do you want to do with #$number ?")
            positiveButton(text = "watch via VLC") {
                openInVlc(this@EpisodeListActivity, link)
            }
            negativeButton(text = "download") {
                val downloadRequest = DownloadManager.Request(link.toUri())
                val downloadTitle = "${titleEntry.info.title}_${number}.mp4"
                val description = "Downloading episode #$number of ${titleEntry.info.title}"
                downloadRequest.setTitle(downloadTitle)
                downloadRequest.setDescription(description)
                downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                downloadRequest.setVisibleInDownloadsUi(true)
                downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizeForFileName(downloadTitle))
                downloadManager.enqueue(downloadRequest)
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
            Glide
                .with(this@EpisodeListActivity)
                .load(data[pos].image)
                .apply(RequestOptions.circleCropTransform())
                .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_DURATION))
                .into(holder.image)
            val number = data[pos].num
            holder.text.text = number.toString()
            holder.view.setOnClickListener {
                if (holder.loading.visibility != View.GONE || job?.isActive != false) {
                    return@setOnClickListener
                }
                job = coroutineScope.launch {
                    try {
                        holder.loading.visibility = View.VISIBLE
                        val links = withContext(Dispatchers.IO) {
                            provider.getStorageLinks(
                                titleEntry.info.title,
                                number
                            )
                        }
                        val link = links[Quality.q720]
                        if (link != null) {
                            val storage = StorageLocator.locate(link)
                            if (storage != null) {
                                val streamLink = withContext(Dispatchers.IO) { storage.extractDownload(link) }
                                println(streamLink)
                                presentLinkToTheUser(number, streamLink)
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
