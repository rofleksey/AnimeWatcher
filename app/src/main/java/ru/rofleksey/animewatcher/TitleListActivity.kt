package ru.rofleksey.animewatcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color.argb
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.mikepenz.iconics.view.IconicsImageButton
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.effet.RippleEffect
import com.takusemba.spotlight.shape.Circle
import jp.wasabeef.recyclerview.animators.ScaleInAnimator
import kotlinx.coroutines.*
import ru.rofleksey.animewatcher.api.provider.ProviderFactory
import ru.rofleksey.animewatcher.database.TitleStorage
import ru.rofleksey.animewatcher.database.TitleStorageEntry
import ru.rofleksey.animewatcher.util.Util


class TitleListActivity : AppCompatActivity() {
    companion object {
        private const val CROSSFADE_DURATION = 500
        private const val ITEM_MARGIN = 25
        private const val SPOTLIGHT_KEY = "intro_title"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var imm: InputMethodManager

    private lateinit var actionBarView: ViewGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var adapter: TitleEntryAdapter
    private lateinit var searchButton: IconicsImageButton
    private lateinit var contentView: ViewGroup
    private lateinit var emptyBackground: LottieAnimationView
    private lateinit var emptyText: TextView

    private lateinit var titleStorage: TitleStorage
    private var spotlight: Spotlight? = null

    private val titleData = ArrayList<TitleStorageEntry>()
    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title_list)

        contentView = findViewById(R.id.title_activity_parent)

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
            sharedPreferences.edit().putBoolean(SPOTLIGHT_KEY, true).apply()
            spotlight?.finish()
            val searchIntent = Intent(this, SearchActivity::class.java)
            //searchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(searchIntent)
        }

        emptyBackground = findViewById(R.id.empty_background)
        emptyText = findViewById(R.id.empty_text)

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
            //            if (providerName == ProviderFactory.ANIMEPAHE ||
//                titleStorage.entryList.any { it.provider == ProviderFactory.ANIMEPAHE }
//            ) {
//                ProviderFactory.init(this@TitleListActivity, sharedPreferences) {
//                    loadingText.text = it
//                }
//            }
            titleData.clear()
            titleData.addAll(titleStorage.entryList)
            adapter.notifyItemRangeInserted(0, titleData.size)
            // TODO: ANIMATION
            recyclerView.visibility = View.VISIBLE
            actionBarView.visibility = View.VISIBLE
            checkEmptyBackground()
            delay(1000)
            if (!sharedPreferences.contains(SPOTLIGHT_KEY)) {
                val spotlightView =
                    Util.spotlightLayout(this@TitleListActivity, "Click here to start searching!")
                val spotlightText: TextView = spotlightView.findViewById(R.id.spotlight_text)
                val searchButtonTarget = Target.Builder()
                    //.setAnchor(searchButton)
                    .setAnchor(searchButton)
                    .setShape(Circle(150f))
                    .setOverlay(spotlightView)
                    .setEffect(RippleEffect(150f, 300f, argb(30, 124, 255, 90)))
                    .setOnTargetListener(object : OnTargetListener {
                        override fun onStarted() {
                            YoYo
                                .with(Techniques.ZoomIn)
                                .duration(500L)
                                .playOn(spotlightText)
                        }

                        override fun onEnded() {

                        }
                    })
                    .build()
                spotlight = Spotlight.Builder(this@TitleListActivity)
                    .setTargets(searchButtonTarget)
                    .setBackgroundColor(R.color.colorSpotlight)
                    .setDuration(250L)
                    .setAnimation(DecelerateInterpolator(2f))
                    .build()
                spotlight?.start()
            }
        }
    }

    fun checkEmptyBackground() {
        if (titleData.isEmpty()) {
            emptyBackground.visibility = View.VISIBLE
            emptyText.visibility = View.VISIBLE
        } else {
            emptyBackground.visibility = View.GONE
            emptyText.visibility = View.GONE
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
        checkEmptyBackground()
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
                        checkEmptyBackground()
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
