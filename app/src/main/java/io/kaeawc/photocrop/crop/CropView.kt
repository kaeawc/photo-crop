package io.kaeawc.photocrop.crop

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView

class CropView : ImageView {

    private var minimumRatio = DEFAULT_MINIMUM_RATIO
    private var maximumRatio = DEFAULT_MAXIMUM_RATIO
    private var defaultRatio = DEFAULT_RATIO

    private var imageUri: Uri? = null
    private var imageRawWidth: Int = 0
    private var imageRawHeight: Int = 0

    private var makeDrawableTask: MakeDrawableTask? = null

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val gridDrawable = GridDrawable()

    private var drawble: Drawable? = null

    private var drawableScale: Float = 0.toFloat()
    private var scaleFocusX: Float = 0.toFloat()
    private var scaleFocusY: Float = 0.toFloat()

    private var displayDrawableLeft: Float = 0.toFloat()
    private var displayDrawableTop: Float = 0.toFloat()

    private val helperRect = RectF()

    private var gestureDetector: GestureDetector? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null

    private var maximumOverScroll: Float = 0.toFloat()

    private var animator: ValueAnimator? = null

    private val mGridCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) { invalidate() }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
    }

    private val isMakingDrawableForView: Boolean
        get() = makeDrawableTask != null

    private val imageSizeRatio: Float
        get() = imageRawWidth.toFloat() / imageRawHeight.toFloat()

    private val drawableScaleToFitWithValidRatio: Float
        get() {
            val scale: Float

            val drawableSizeRatio = imageSizeRatio
            val imageSizeRatioIsValid = isImageSizeRatioValid(drawableSizeRatio)

            if (imageSizeRatioIsValid) {
                val viewRatio = width.toFloat() / height.toFloat()
                val drawableRatio = imageRawWidth.toFloat() / imageRawHeight.toFloat()

                val drawableIsWiderThanView = drawableRatio > viewRatio

                if (drawableIsWiderThanView) {
                    scale = width.toFloat() / imageRawWidth.toFloat()
                } else {
                    scale = height.toFloat() / imageRawHeight.toFloat()
                }
            } else if (imageRawWidth < width || imageRawHeight < height) {
                if (drawableSizeRatio < maximumRatio) {
                    getBoundsForWidthAndRatio(imageRawWidth.toFloat(), minimumRatio, helperRect)
                    scale = helperRect.height() / height.toFloat()
                } else {
                    getBoundsForHeightAndRatio(imageRawHeight.toFloat(), maximumRatio, helperRect)
                    scale = helperRect.width() / width.toFloat()
                }
            } else {
                if (drawableSizeRatio < minimumRatio) {
                    getBoundsForHeightAndRatio(height.toFloat(), minimumRatio, helperRect)
                    scale = helperRect.width() / imageRawWidth
                } else {
                    getBoundsForWidthAndRatio(width.toFloat(), maximumRatio, helperRect)
                    scale = helperRect.height() / imageRawHeight
                }
            }

            return scale
        }

    private val displayDrawableWidth: Float
        get() = drawableScale * imageRawWidth

    private val displayDrawableHeight: Float
        get() = drawableScale * imageRawHeight

    private val onGestureListener = object : GestureDetector.OnGestureListener {

        override fun onDown(motionEvent: MotionEvent): Boolean = true
        override fun onShowPress(motionEvent: MotionEvent) {}
        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean = false
        override fun onLongPress(motionEvent: MotionEvent) {}
        override fun onFling(motionEvent: MotionEvent, motionEvent1: MotionEvent, v: Float, v1: Float): Boolean = false
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            getDisplayDrawableBounds(helperRect)
            val overScrollX = measureOverScrollX(helperRect)
            val overScrollY = measureOverScrollY(helperRect)
            displayDrawableLeft += applyOverScrollFix(-distanceX, overScrollX)
            displayDrawableTop += applyOverScrollFix(-distanceY, overScrollY)
            updateGrid()
            invalidate()
            return true
        }
    }

    private val onScaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val overScale = measureOverScale()
            val scale = applyOverScaleFix(detector.scaleFactor, overScale)

            scaleFocusX = detector.focusX
            scaleFocusY = detector.focusY

            setScaleKeepingFocus(drawableScale * scale, scaleFocusX, scaleFocusY)

            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {}

    }

    private val maximumAllowedScale: Float
        get() {
            val maximumAllowedWidth = imageRawWidth.toFloat()
            val maximumAllowedHeight = imageRawHeight.toFloat()

            return Math.min(maximumAllowedWidth / width.toFloat(), maximumAllowedHeight / height.toFloat())
        }

    private val minimumAllowedScale: Float
        get() = drawableScaleToFitWithValidRatio

    private val settleAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Float

        getDisplayDrawableBounds(helperRect)

        val overScrollX = measureOverScrollX(helperRect)
        val overScrollY = measureOverScrollY(helperRect)
        val overScale = measureOverScale()

        displayDrawableLeft -= overScrollX * animatedValue
        displayDrawableTop -= overScrollY * animatedValue

        val targetScale = drawableScale / overScale
        val newScale = (1 - animatedValue) * drawableScale + animatedValue * targetScale

        setScaleKeepingFocus(newScale, scaleFocusX, scaleFocusY)

        updateGrid()
        invalidate()
    }

    interface BitmapCallback {
        fun onBitmapReady(bitmap: Bitmap)
    }

    constructor(context: Context) : super(context) {
        initialize(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr, 0)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        gestureDetector = GestureDetector(context, onGestureListener)
        scaleGestureDetector = ScaleGestureDetector(context, onScaleGestureListener)

        maximumOverScroll = resources.displayMetrics.density * MAXIMUM_OVER_SCROLL

        animator = ValueAnimator()
        animator!!.duration = SET_BACK_DURATION
        animator!!.setFloatValues(0f, 1f)
        animator!!.interpolator = DecelerateInterpolator(0.25f)
        animator!!.addUpdateListener(settleAnimatorUpdateListener)

        gridDrawable.callback = mGridCallback
    }

    fun setRatios(defaultRatio: Float, minimumRatio: Float, maximumRatio: Float) {
        this.defaultRatio = defaultRatio
        this.minimumRatio = minimumRatio
        this.maximumRatio = maximumRatio

        if (animator!!.isRunning) {
            animator!!.cancel()
        }

        cancelMakingDrawableProcessIfExists()

        drawble = null

        requestLayout()
    }

    fun setImageUri(uri: Uri) {
        cancelMakingDrawableProcessIfExists()

        imageUri = uri
        drawble = null

        requestLayout()
        invalidate()
    }

    fun crop(widthSpec: Int, heightSpec: Int) {
        if (imageUri == null) {
            throw IllegalStateException("Image uri is not set.")
        }

        if (drawble == null || animator!!.isRunning) {
            postDelayed({ crop(widthSpec, heightSpec) }, SET_BACK_DURATION / 2)
            return
        }

        val gridBounds = RectF(gridDrawable.bounds)
        gridBounds.offset(-displayDrawableLeft, -displayDrawableTop)

        getDisplayDrawableBounds(helperRect)

        val leftRatio = gridBounds.left / helperRect.width()
        val topRatio = gridBounds.top / helperRect.height()
        val rightRatio = gridBounds.right / helperRect.width()
        val bottomRatio = gridBounds.bottom / helperRect.height()

        val actualLeft = Math.max(0, (leftRatio * imageRawWidth).toInt())
        val actualTop = Math.max(0, (topRatio * imageRawHeight).toInt())
        val actualRight = Math.min(imageRawWidth, (rightRatio * imageRawWidth).toInt())
        val actualBottom = Math.min(imageRawHeight, (bottomRatio * imageRawHeight).toInt())

        val context = context


    }

    private fun cropImageAndResize(context: Context, left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): Bitmap? {
        var left = left
        var top = top
        var right = right
        var bottom = bottom
        val options = BitmapFactory.Options()
        options.inSampleSize = 1

        val rawArea = (right - left) * (bottom - top)
        val targetArea = width * height * 4

        var resultArea = rawArea

        while (resultArea > targetArea) {
            options.inSampleSize *= 2
            resultArea = rawArea / (options.inSampleSize * options.inSampleSize)
        }

        if (options.inSampleSize > 1) {
            options.inSampleSize /= 2
        }

        try {
            val rawBitmap = MakeDrawableTask.getBitmap(context, imageUri!!, options) ?: return null

            left /= options.inSampleSize
            top /= options.inSampleSize
            right /= options.inSampleSize
            bottom /= options.inSampleSize

            val croppedWidth = right - left
            val croppedHeight = bottom - top

            val croppedBitmap = Bitmap.createBitmap(rawBitmap, left, top, croppedWidth, croppedHeight)

            if (rawBitmap != croppedBitmap) {
                rawBitmap.recycle()
            }

            if (croppedWidth <= width && croppedHeight <= height) {
                return croppedBitmap
            }

            val resizedBitmap = MakeDrawableTask.resizeBitmap(croppedBitmap, width, height)

            croppedBitmap.recycle()

            return resizedBitmap
        } catch (t: Throwable) {
            return null
        }

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        var targetWidth = 1
        var targetHeight = 1

        when (widthMode) {
            View.MeasureSpec.EXACTLY -> {
                targetWidth = widthSize

                when (heightMode) {
                    View.MeasureSpec.EXACTLY -> targetHeight = heightSize
                    View.MeasureSpec.AT_MOST -> targetHeight = Math.min(heightSize, (targetWidth / defaultRatio).toInt())
                    View.MeasureSpec.UNSPECIFIED -> targetHeight = (targetWidth / defaultRatio).toInt()
                }
            }
            View.MeasureSpec.AT_MOST -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> {
                    targetHeight = heightSize
                    targetWidth = Math.min(widthSize, (targetHeight * defaultRatio).toInt())
                }
                View.MeasureSpec.AT_MOST -> {
                    val specRatio = widthSize.toFloat() / heightSize.toFloat()

                    if (specRatio == defaultRatio) {
                        targetWidth = widthSize
                        targetHeight = heightSize
                    } else if (specRatio > defaultRatio) {
                        targetHeight = heightSize
                        targetWidth = (targetHeight * defaultRatio).toInt()
                    } else {
                        targetWidth = widthSize
                        targetHeight = (targetWidth / defaultRatio).toInt()
                    }
                }
                View.MeasureSpec.UNSPECIFIED -> {
                    targetWidth = widthSize
                    targetHeight = (targetWidth / defaultRatio).toInt()
                }
            }
            View.MeasureSpec.UNSPECIFIED -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> {
                    targetHeight = heightSize
                    targetWidth = (targetHeight * defaultRatio).toInt()
                }
                View.MeasureSpec.AT_MOST -> {
                    targetHeight = heightSize
                    targetWidth = (targetHeight * defaultRatio).toInt()
                }
                View.MeasureSpec.UNSPECIFIED -> {
                    targetWidth = maximumOverScroll.toInt()
                    targetHeight = maximumOverScroll.toInt()
                }
            }
        }

        setMeasuredDimension(targetWidth, targetHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        imageWidth = right - left
        imageHeight = bottom - top

        if (width == 0 || height == 0) {
            return
        }

        if (imageUri == null) {
            return
        }

        if (currentDrawableIsSuitableForView()) {
            cancelMakingDrawableProcessIfExists()
            return
        }

        if (isMakingDrawableForView) {
            if (drawableBeingMadeIsSuitableForView()) {
                return
            }

            cancelMakingDrawableProcessIfExists()
        }

        startMakingSuitableDrawable()
    }

    private fun currentDrawableIsSuitableForView(): Boolean {
        if (drawble == null) {
            return false
        }

        val drawableWidth = drawble!!.intrinsicWidth
        val drawableHeight = drawble!!.intrinsicHeight

        return isSizeSuitableForView(drawableWidth, drawableHeight)
    }

    private fun cancelMakingDrawableProcessIfExists() {
        if (makeDrawableTask != null) {
            makeDrawableTask!!.cancel(true)
            makeDrawableTask = null
        }
    }

    private fun drawableBeingMadeIsSuitableForView(): Boolean {
        return isSizeSuitableForView(makeDrawableTask!!.targetWidth, makeDrawableTask!!.targetHeight)
    }

    private fun isSizeSuitableForView(width: Int, height: Int): Boolean {
        val viewArea = width * height
        val drawableArea = width * height

        val areaRatio = viewArea.toFloat() / drawableArea.toFloat()

        return areaRatio >= 0.5f && areaRatio <= 2f
    }

    private fun startMakingSuitableDrawable() {
        makeDrawableTask = object : MakeDrawableTask(context, imageUri!!, width, height) {

            override fun onPostExecute(drawable: Drawable) {
                drawble = drawable

                imageRawWidth = rawWidth
                imageRawHeight = rawHeight

                onDrawableChanged()
            }

        }

        makeDrawableTask!!.execute()
    }

    private fun onDrawableChanged() {
        reset()
    }

    private fun reset() {
        if (animator!!.isRunning) {
            animator!!.cancel()
        }

        scaleDrawableToFitWithinViewWithValidRatio()

        placeDrawableInTheCenter()

        updateGrid()

        invalidate()
    }

    private fun isImageSizeRatioValid(imageSizeRatio: Float): Boolean =
            imageSizeRatio in minimumRatio..maximumRatio

    private fun scaleDrawableToFitWithinViewWithValidRatio() {
        val scale = drawableScaleToFitWithValidRatio

        setDrawableScale(scale)
    }

    private fun setDrawableScale(scale: Float) {
        drawableScale = scale

        invalidate()
    }

    private fun placeDrawableInTheCenter() {
        displayDrawableLeft = (width - displayDrawableWidth) / 2
        displayDrawableTop = (height - displayDrawableHeight) / 2

        invalidate()
    }

    private fun updateGrid() {
        getDisplayDrawableBounds(helperRect)

        helperRect.intersect(0f, 0f, width.toFloat(), height.toFloat())

        val gridLeft = helperRect.left
        val gridTop = helperRect.top

        val gridWidth = helperRect.width()
        val gridHeight = helperRect.height()

        helperRect.set(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)
        setGridBounds(helperRect)

        invalidate()
    }

    private fun getBoundsForWidthAndRatio(width: Float, ratio: Float, rect: RectF) {
        val height = width / ratio

        rect.set(0f, 0f, width, height)
    }

    private fun getBoundsForHeightAndRatio(height: Float, ratio: Float, rect: RectF) {
        val width = height * ratio

        rect.set(0f, 0f, width, height)
    }

    private fun setGridBounds(bounds: RectF) {
        gridDrawable.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())

        invalidate()
    }

    private fun getDisplayDrawableBounds(bounds: RectF) {
        bounds.left = displayDrawableLeft
        bounds.top = displayDrawableTop
        bounds.right = bounds.left + displayDrawableWidth
        bounds.bottom = bounds.top + displayDrawableHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (drawble == null) {
            return
        }

        getDisplayDrawableBounds(helperRect)

        drawble!!.setBounds(helperRect.left.toInt(), helperRect.top.toInt(), helperRect.right.toInt(), helperRect.bottom.toInt())
        drawble!!.draw(canvas)

        gridDrawable.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawble == null) {
            return false
        }

        gestureDetector!!.onTouchEvent(event)
        scaleGestureDetector!!.onTouchEvent(event)

        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
            animator!!.start()
        }

        return true
    }

    private fun measureOverScrollX(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.width() <= width
        if (drawableIsSmallerThanView) return displayDrawableBounds.centerX() - width / 2
        if (displayDrawableBounds.left <= 0 && displayDrawableBounds.right >= width) return 0f
        if (displayDrawableBounds.left < 0) return displayDrawableBounds.right - width
        return if (displayDrawableBounds.right > width) displayDrawableBounds.left else 0f
    }

    private fun measureOverScrollY(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.height() < height
        if (drawableIsSmallerThanView) return displayDrawableBounds.centerY() - height / 2
        if (displayDrawableBounds.top <= 0 && displayDrawableBounds.bottom >= height) return 0f
        if (displayDrawableBounds.top < 0) return displayDrawableBounds.bottom - height
        return if (displayDrawableBounds.bottom > height) displayDrawableBounds.top else 0f
    }

    private fun applyOverScrollFix(distance: Float, overScroll: Float): Float {
        if (overScroll * distance <= 0) return distance
        val offRatio = Math.abs(overScroll) / maximumOverScroll
        return distance - (distance * Math.sqrt(offRatio.toDouble())).toFloat()
    }

    private fun measureOverScale(): Float {
        var maximumAllowedScale = maximumAllowedScale
        val minimumAllowedScale = minimumAllowedScale

        if (maximumAllowedScale < minimumAllowedScale) {
            maximumAllowedScale = minimumAllowedScale
        }

        return if (drawableScale < minimumAllowedScale) {
            drawableScale / minimumAllowedScale
        } else if (drawableScale > maximumAllowedScale) {
            drawableScale / maximumAllowedScale
        } else {
            1f
        }
    }

    private fun applyOverScaleFix(scale: Float, overScale: Float): Float {
        if (overScale == 1f) {
            return scale
        }

        val maxScale = if (overScale > 1) {
            1f / overScale
        } else {
            overScale
        }

        var wentOverScaleRatio = (maxScale - MAXIMUM_OVER_SCALE) / (1 - MAXIMUM_OVER_SCALE)

        if (wentOverScaleRatio < 0) {
            wentOverScaleRatio = 0f
        }

        // 1 -> scale , 0 -> 1
        // scale * f(1) = scale
        // scale * f(0) = 1

        // f(1) = 1
        // f(0) = 1/scale

        return scale * wentOverScaleRatio + (1 - wentOverScaleRatio) / scale
    }

    private fun setScaleKeepingFocus(scale: Float, focusX: Float, focusY: Float) {
        getDisplayDrawableBounds(helperRect)

        val focusRatioX = (focusX - helperRect.left) / helperRect.width()
        val focusRatioY = (focusY - helperRect.top) / helperRect.height()

        drawableScale = scale

        getDisplayDrawableBounds(helperRect)

        val scaledFocusX = helperRect.left + focusRatioX * helperRect.width()
        val scaledFocusY = helperRect.top + focusRatioY * helperRect.height()

        displayDrawableLeft += focusX - scaledFocusX
        displayDrawableTop += focusY - scaledFocusY

        updateGrid()
        invalidate()
    }

    companion object {

        val DEFAULT_MINIMUM_RATIO = 4f / 5f
        val DEFAULT_MAXIMUM_RATIO = 1.91f
        val DEFAULT_RATIO = 1f

        private val MAXIMUM_OVER_SCROLL = 144f
        private val MAXIMUM_OVER_SCALE = 0.7f

        private val SET_BACK_DURATION: Long = 400
    }


//    object Asdf : AsyncTask<Void, Void, Bitmap>() {
//
//        override fun doInBackground(vararg params: Void): Bitmap? {
//            val actualWidth = actualRight - actualLeft
//            val actualHeight = actualBottom - actualTop
//            var actualRatio = actualWidth.toFloat() / actualHeight.toFloat()
//
//            if (actualRatio < minimumRatio) {
//                actualRatio = minimumRatio
//            }
//
//            if (actualRatio > maximumRatio) {
//                actualRatio = maximumRatio
//            }
//
//            val widthMode = View.MeasureSpec.getMode(widthSpec)
//            val widthSize = View.MeasureSpec.getSize(widthSpec)
//            val heightMode = View.MeasureSpec.getMode(heightSpec)
//            val heightSize = View.MeasureSpec.getSize(heightSpec)
//
//            var targetWidth = actualWidth
//            var targetHeight = actualHeight
//
//            when (widthMode) {
//                View.MeasureSpec.EXACTLY -> {
//                    targetWidth = widthSize
//
//                    when (heightMode) {
//                        View.MeasureSpec.EXACTLY -> targetHeight = heightSize
//                        View.MeasureSpec.AT_MOST -> targetHeight = Math.min(heightSize, (targetWidth / actualRatio).toInt())
//                        View.MeasureSpec.UNSPECIFIED -> targetHeight = (targetWidth / actualRatio).toInt()
//                    }
//                }
//                View.MeasureSpec.AT_MOST -> when (heightMode) {
//                    View.MeasureSpec.EXACTLY -> {
//                        targetHeight = heightSize
//                        targetWidth = Math.min(widthSize, (targetHeight * actualRatio).toInt())
//                    }
//                    View.MeasureSpec.AT_MOST -> if (actualWidth <= widthSize && actualHeight <= heightSize) {
//                        targetWidth = actualWidth
//                        targetHeight = actualHeight
//                    } else {
//                        val specRatio = widthSize.toFloat() / heightSize.toFloat()
//
//                        if (specRatio == actualRatio) {
//                            targetWidth = widthSize
//                            targetHeight = heightSize
//                        } else if (specRatio > actualRatio) {
//                            targetHeight = heightSize
//                            targetWidth = (targetHeight * actualRatio).toInt()
//                        } else {
//                            targetWidth = widthSize
//                            targetHeight = (targetWidth / actualRatio).toInt()
//                        }
//                    }
//                    View.MeasureSpec.UNSPECIFIED -> if (actualWidth <= widthSize) {
//                        targetWidth = actualWidth
//                        targetHeight = actualHeight
//                    } else {
//                        targetWidth = widthSize
//                        targetHeight = (targetWidth / actualRatio).toInt()
//                    }
//                }
//                View.MeasureSpec.UNSPECIFIED -> when (heightMode) {
//                    View.MeasureSpec.EXACTLY -> {
//                        targetHeight = heightSize
//                        targetWidth = (targetHeight * actualRatio).toInt()
//                    }
//                    View.MeasureSpec.AT_MOST -> if (actualHeight <= heightSize) {
//                        targetHeight = actualHeight
//                        targetWidth = actualWidth
//                    } else {
//                        targetHeight = heightSize
//                        targetWidth = (targetHeight * actualRatio).toInt()
//                    }
//                    View.MeasureSpec.UNSPECIFIED -> {
//                        targetWidth = actualWidth
//                        targetHeight = actualHeight
//                    }
//                }
//            }
//
//            return cropImageAndResize(context, actualLeft, actualTop, actualRight, actualBottom, targetWidth, targetHeight)
//        }
//
//        override fun onPostExecute(bitmap: Bitmap) {
//            callback.onBitmapReady(bitmap)
//        }
//
//    }
}
