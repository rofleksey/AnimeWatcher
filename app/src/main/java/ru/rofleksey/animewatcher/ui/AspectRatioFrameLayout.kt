package ru.rofleksey.animewatcher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout


class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) :
    FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "AspectRatioFrameLayout"
    }

    private var videoAspectRatio = 1.7777778f

    fun setAspectRatio(widthHeightRatio: Float) {
        if (videoAspectRatio != widthHeightRatio) {
            videoAspectRatio = widthHeightRatio
            Log.v(TAG, "videoAspectRatio = $videoAspectRatio")
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (videoAspectRatio <= 0) { // Aspect ratio not set.
            return
        }
        var width = measuredWidth
        var height = measuredHeight
        val viewAspectRatio = width.toFloat() / height
        val aspectDeformation = videoAspectRatio / viewAspectRatio - 1
        if (aspectDeformation > 0) {
            height = (width / videoAspectRatio).toInt()
        } else {
            width = (height * videoAspectRatio).toInt()
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }
}
