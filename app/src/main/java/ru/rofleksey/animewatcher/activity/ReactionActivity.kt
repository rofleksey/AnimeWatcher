package ru.rofleksey.animewatcher.activity

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import kotlinx.android.synthetic.main.activity_reaction.*
import kotlinx.coroutines.*
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import ru.rofleksey.animewatcher.FfmpegService
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.util.AnimeUtils
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random


class ReactionActivity : AppCompatActivity() {
    companion object {
        const val TAG = "ReactionActivity"
        const val ARG_FILE = "argFile"
        const val SELECT_REACTION = 1337
    }

    private lateinit var tempFile: String

    private var reactionFile: String? = null
    private var reactionStart: String = "1:00"
    private var episodeStart: String = "0:00"
    private var reactorVolume: Float = 0.5f
    private var episodeScale: Float = 0.5f
    private var position: Int = 0
    private lateinit var episodeFile: String

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction)

        tempFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "preview.mp4"
        ).toString()

        episodeFile = intent.getStringExtra(ARG_FILE) ?: throw Exception("No episode file passed")

        if (savedInstanceState != null) {
            reactionFile = savedInstanceState.getString("reactionFile")
            reactionStart = savedInstanceState.getString("reactionStart", "1:00")
            episodeStart = savedInstanceState.getString("episodeStart", "0:00")
            reactorVolume = savedInstanceState.getFloat("reactorVolume")
            episodeScale = savedInstanceState.getFloat("episodeScale")
            position = savedInstanceState.getInt("position")
        }

        setReactionFile(reactionFile)
        setPosition(position)
        setOtherFields(reactionStart, episodeStart, reactorVolume, episodeScale)
        //
        button_file.setOnClickListener {
            selectReaction()
        }
        button_reaction.setOnClickListener {
            MaterialDialog(this).show {
                input(hint = "1:00", prefill = reactionStart) { dialog, text ->
                    setOtherFields(text.toString(), episodeStart, reactorVolume, episodeScale)
                }
                positiveButton(R.string.ok)
            }
        }
        button_episode.setOnClickListener {
            MaterialDialog(this).show {
                input(hint = "0:00", prefill = episodeStart) { dialog, text ->
                    setOtherFields(reactionStart, text.toString(), reactorVolume, episodeScale)
                }
                positiveButton(R.string.ok)
            }
        }
        seekbar_volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setOtherFields(
                        reactionStart,
                        episodeStart,
                        progress / 100f,
                        episodeScale,
                        false
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })
        seekbar_scale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setOtherFields(
                        reactionStart,
                        episodeStart,
                        reactorVolume,
                        progress / 100f,
                        false
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })
        val list =
            listOf(button_bottom_left, button_top_left, button_top_right, button_bottom_right)
        list.forEachIndexed { ind, button ->
            button.setOnClickListener {
                setPosition(ind)
            }
        }
        button_test.setOnClickListener {
            if (reactionFile == null || job?.isActive == true) {
                return@setOnClickListener
            }
            val (x, y) = getPosition()
            process(
                arrayOf(
                    "-ss", reactionStart, "-i", reactionFile!!,
                    "-ss", episodeStart, "-i", episodeFile,
                    "-y", "-threads", (2 * Runtime.getRuntime().availableProcessors()).toString(),
                    "-filter_complex",
                    "[0:v]setpts=PTS-STARTPTS[reaction];[1:v]setpts=PTS-STARTPTS,scale=iw*${episodeScale}:ih*${episodeScale}[episode];" +
                            "[reaction][episode]overlay=x=${x}:y=${y}:eof_action=pass;[0:a]volume=volume=${reactorVolume}[rsound];[1:a]volume=volume=${1 - reactorVolume}[esound];[rsound][esound]amix=inputs=2[a]",
                    "-map", "[a]", "-shortest",
                    "-f", "mp4", "-acodec", "aac",
                    "-vcodec", "libx264", "-crf", "18", "-preset", "ultrafast",
                    "-t", "10", tempFile, "-hide_banner"
                ),
                false
            )
        }
        button_start.setOnClickListener {
            if (reactionFile == null || job?.isActive == true) {
                return@setOnClickListener
            }
            val (x, y) = getPosition()
            val outputFileName = "${File(episodeFile).name}_${Random.nextLong()}.mp4"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                outputFileName
            ).toString()
            process(
                arrayOf(
                    "-ss", reactionStart, "-i", reactionFile!!,
                    "-ss", episodeStart, "-i", episodeFile,
                    "-y", "-threads", (2 * Runtime.getRuntime().availableProcessors()).toString(),
                    "-filter_complex",
                    "[0:v]setpts=PTS-STARTPTS[reaction];[1:v]setpts=PTS-STARTPTS,scale=iw*${episodeScale}:ih*${episodeScale}[episode];" +
                            "[reaction][episode]overlay=x=${x}:y=${y}:eof_action=pass;[0:a]volume=volume=${reactorVolume}[rsound];[1:a]volume=volume=${1 - reactorVolume}[esound];[rsound][esound]amix=inputs=2[a]",
                    "-map", "[a]", "-shortest",
                    "-f", "mp4", "-acodec", "aac",
                    "-vcodec", "libx264", "-crf", "18", "-preset", "fast",
                    file, "-hide_banner"
                ),
                true
            )
        }
    }

    data class FfmpegPosition(val x: String, val y: String)

    private fun getPosition(): FfmpegPosition {
        val left = position == 0 || position == 1
        val top = position == 1 || position == 2
        val x = if (left) "0" else "main_w-overlay_w"
        val y = if (top) "0" else "main_h-overlay_h"
        return FfmpegPosition(x, y)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("reactionFile", reactionFile)
        outState.putString("reactionStart", reactionStart)
        outState.putString("episodeStart", episodeStart)
        outState.putFloat("reactorVolume", reactorVolume)
        outState.putFloat("episodeScale", episodeScale)
        outState.putInt("position", position)
    }

    private fun selectReaction() {
        val videoIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        videoIntent.type = "video/*"
        startActivityForResult(videoIntent, SELECT_REACTION)
    }

    private fun setOtherFields(
        rStart: String,
        eStart: String,
        volume: Float,
        scale: Float,
        modifySeekBars: Boolean = true
    ) {
        reactionStart = rStart
        episodeStart = eStart
        reactorVolume = volume
        episodeScale = scale
        text_reaction.text = rStart
        text_episode.text = eStart
        if (modifySeekBars) {
            seekbar_scale.progress = (scale * 100).toInt()
            seekbar_volume.progress = (volume * 100).toInt()
        }
        text_volume.text = (volume * 100).toInt().toString()
        text_scale.text = (scale * 100).toInt().toString()
    }

    private fun setPosition(which: Int) {
        position = which
        val list =
            listOf(button_bottom_left, button_top_left, button_top_right, button_bottom_right)
        list.forEachIndexed { ind, button ->
            button.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    when (ind) {
                        which -> R.color.colorOrange
                        else -> R.color.colorTransparent
                    }
                )
            )
        }
    }

    private fun setReactionFile(file: String?) {
        reactionFile = file
        Log.v(TAG, "reaction = $file")
        if (file == null) {
            group_file.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            button_test.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            button_start.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            text_file.text = ""
        } else {
            group_file.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            button_test.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            button_start.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            text_file.text = file
        }
    }

    private fun process(arguments: Array<String>, background: Boolean) {
        if (!background) {
            job?.cancel()
            job = coroutineScope.launch {
                try {
                    AnimeUtils.toast(this@ReactionActivity, "processing...")
                    val app = FFmpeg.getInstance(this@ReactionActivity)
                    if (!app.isSupported) {
                        throw Exception("FFmpeg is not supported")
                    }
                    val result = suspendCancellableCoroutine<String> { cont ->
                        val task = app.execute(
                            arguments, object :
                                ExecuteBinaryResponseHandler() {
                                override fun onFailure(message: String) {
                                    cont.resumeWithException(Exception(message))
                                }

                                override fun onProgress(message: String?) {
                                    Log.v(TAG, message ?: "")
                                }

                                override fun onSuccess(message: String) {
                                    cont.resume(message)
                                }
                            })

                        cont.invokeOnCancellation {
                            task.sendQuitSignal()
                        }
                    }
                    Log.v(TAG, "result = $result")
                    AnimeUtils.vibrate(this@ReactionActivity, 20)
                    AnimeUtils.toast(this@ReactionActivity, "Done!")
                    AnimeUtils.openVideo(this@ReactionActivity, tempFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    AnimeUtils.toast(this@ReactionActivity, "error: ${e.message}")
                } finally {

                }
            }
        } else {
            val intent = Intent(this, FfmpegService::class.java)
            intent.putExtra(FfmpegService.ARG_ARGUMENTS, arguments)
            startService(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_REACTION && resultCode == RESULT_OK && data != null) {
            val payload = data.data ?: return
            setReactionFile(AnimeUtils.getPickerPath(this, payload))
        }
    }
}
