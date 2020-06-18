package ru.rofleksey.animewatcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import ru.rofleksey.animewatcher.R

class PlayerProgressView : View {

    private val paint = Paint()
    private val rect = Rect()

    private var temp_progress: Float = 0f

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        paint.color = ContextCompat.getColor(context, R.color.colorGray)
    }

    fun setProgress(progress: Long, max: Long) {
        temp_progress = progress / max.toFloat()
        rect.set(0, 0, (width * temp_progress).toInt(), height)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(rect, paint)
    }
}
