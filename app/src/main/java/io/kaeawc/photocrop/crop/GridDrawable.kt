package io.kaeawc.photocrop.crop

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class GridDrawable : Drawable() {

    private val linePaint = Paint()
    private val lineBorderPaint = Paint()
    private val animator = ValueAnimator()
    private var alpha = 1f

    private val mAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { animation ->
        alpha = animation.animatedValue as Float

        invalidateSelf()
    }

    init {

        linePaint.style = Paint.Style.STROKE
        linePaint.color = LINE_COLOR
        linePaint.strokeWidth = LINE_STROKE_WIDTH

        lineBorderPaint.style = Paint.Style.STROKE
        lineBorderPaint.color = LINE_BORDER_COLOR
        lineBorderPaint.strokeWidth = LINE_STROKE_WIDTH

        animator.duration = TIME_TO_FADE
        animator.startDelay = TIME_BEFORE_FADE
        animator.setFloatValues(1f, 0f)
        animator.addUpdateListener(mAnimatorUpdateListener)
        animator.interpolator = LinearInterpolator()

        animator.start()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)

        alpha = 1f
        invalidateSelf()

        animator.cancel()
        animator.start()
    }

    override fun draw(canvas: Canvas) {
        linePaint.alpha = Math.round(alpha * 255)
        lineBorderPaint.alpha = Math.round(alpha * 0x44)

        val bounds = bounds

        val width = bounds.width()
        val height = bounds.height()

        val left = bounds.left + width / 3
        val right = left + width / 3
        val top = bounds.top + height / 3
        val bottom = top + height / 3

        canvas.drawLine((left - 1).toFloat(), bounds.top.toFloat(), (left - 1).toFloat(), bounds.bottom.toFloat(), lineBorderPaint)
        canvas.drawLine((left + 1).toFloat(), bounds.top.toFloat(), (left + 1).toFloat(), bounds.bottom.toFloat(), lineBorderPaint)

        canvas.drawLine((right - 1).toFloat(), bounds.top.toFloat(), (right - 1).toFloat(), bounds.bottom.toFloat(), lineBorderPaint)
        canvas.drawLine((right + 1).toFloat(), bounds.top.toFloat(), (right + 1).toFloat(), bounds.bottom.toFloat(), lineBorderPaint)

        canvas.drawLine(bounds.left.toFloat(), (top - 1).toFloat(), bounds.right.toFloat(), (top - 1).toFloat(), lineBorderPaint)
        canvas.drawLine(bounds.left.toFloat(), (top + 1).toFloat(), bounds.right.toFloat(), (top + 1).toFloat(), lineBorderPaint)

        canvas.drawLine(bounds.left.toFloat(), (bottom - 1).toFloat(), bounds.right.toFloat(), (bottom - 1).toFloat(), lineBorderPaint)
        canvas.drawLine(bounds.left.toFloat(), (bottom + 1).toFloat(), bounds.right.toFloat(), (bottom + 1).toFloat(), lineBorderPaint)

        canvas.drawLine(left.toFloat(), bounds.top.toFloat(), left.toFloat(), bounds.bottom.toFloat(), linePaint)
        canvas.drawLine(right.toFloat(), bounds.top.toFloat(), right.toFloat(), bounds.bottom.toFloat(), linePaint)
        canvas.drawLine(bounds.left.toFloat(), top.toFloat(), bounds.right.toFloat(), top.toFloat(), linePaint)
        canvas.drawLine(bounds.left.toFloat(), bottom.toFloat(), bounds.right.toFloat(), bottom.toFloat(), linePaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {

        private val LINE_COLOR = Color.WHITE
        private val LINE_BORDER_COLOR = 0x44888888
        private val LINE_STROKE_WIDTH = 1f
        private val TIME_BEFORE_FADE: Long = 300
        private val TIME_TO_FADE: Long = 300
    }

}