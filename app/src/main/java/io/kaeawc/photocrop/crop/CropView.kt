package io.kaeawc.photocrop.crop

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
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
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import timber.log.Timber

class CropView : View, Target<Drawable> {

    companion object {

        const val MINIMUM_RATIO = 5f / 8f
        const val MAXIMUM_RATIO = 1.9f
        const val DEFAULT_RATIO = 1f

        private const val MAXIMUM_OVER_SCROLL = 144f
        private const val MAXIMUM_OVER_SCALE = 0.7f

        private const val SET_BACK_DURATION: Long = 400
    }

    private var uriReference: Uri? = null
    private var imageRawWidth: Int = 0

    private var imageRawHeight: Int = 0

    private var makeDrawableTask: MakeSuitableTask? = null

    private var displayWidth: Int = 0
    private var displayHeight: Int = 0

    private val viewportGrid = GridDrawable()

    // Once the Bitmap is loaded from disk or remote it is cached and manipulated in a Drawable
    private var viewportImage: Drawable? = null

    // Scale & Translate parameters
    private var viewportScale: Float = 0f
    private var scaleFocusX: Float = 0f
    private var scaleFocusY: Float = 0f
    private var translateLeft: Float = 0f
    private var translateTop: Float = 0f

    private val viewportBounds = RectF()

    private var gestureDetector: GestureDetector? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null

    private var maximumOverScroll: Float = 0f

    private var animator: ValueAnimator? = null

    private val gridCallback = object : Drawable.Callback {

        override fun invalidateDrawable(who: Drawable) {
            invalidate()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
    }

    private val isMakingDrawableForView: Boolean
        get() = makeDrawableTask != null

    private val imageSizeRatio: Float
        get() = imageRawWidth.toFloat() / imageRawHeight.toFloat()

    private val drawableScaleToFitWithValidRatio: Float
        get() {
            val drawableSizeRatio = imageSizeRatio
            val imageSizeRatioIsValid = isImageSizeRatioValid(drawableSizeRatio)
            val height = displayHeight.toFloat()
            val width = displayWidth.toFloat()
            val rawWidth = imageRawWidth.toFloat()
            val rawHeight = imageRawHeight.toFloat()

            return if (imageSizeRatioIsValid) {
                val widthOverHeight = rawWidth / rawHeight
                val heightOverWidth = rawHeight / rawWidth
                val drawableIsWiderThanView = widthOverHeight > heightOverWidth

                val heightRatio = height / rawHeight
                val widthRatio = width / rawWidth

                when {
                    drawableIsWiderThanView -> heightRatio
                    else -> widthRatio
                }
            } else if (rawWidth < width || rawHeight < height) {
                if (drawableSizeRatio < MAXIMUM_RATIO) {
                    getBoundsForWidthAndRatio(rawWidth, MINIMUM_RATIO, viewportBounds)
                    viewportBounds.height() / height
                } else {
                    getBoundsForHeightAndRatio(rawHeight, MAXIMUM_RATIO, viewportBounds)
                    viewportBounds.width() / width
                }
            } else {
                if (drawableSizeRatio < MINIMUM_RATIO) {
                    getBoundsForHeightAndRatio(height, MINIMUM_RATIO, viewportBounds)
                    viewportBounds.width() / rawWidth
                } else {
                    getBoundsForWidthAndRatio(width, MAXIMUM_RATIO, viewportBounds)
                    viewportBounds.height() / rawHeight
                }
            }
        }

    private val displayDrawableWidth: Float
        get() = viewportScale * imageRawWidth

    private val displayDrawableHeight: Float
        get() = viewportScale * imageRawHeight

    private val gestureListener = object : GestureDetector.OnGestureListener {

        override fun onDown(motionEvent: MotionEvent): Boolean = true

        override fun onShowPress(motionEvent: MotionEvent) {}

        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean = false

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            getDisplayDrawableBounds(viewportBounds)
            val overScrollX = measureOverScrollX(viewportBounds)
            val overScrollY = measureOverScrollY(viewportBounds)
            translateLeft += applyOverScrollFix(-distanceX, overScrollX)
            translateTop += applyOverScrollFix(-distanceY, overScrollY)
            updateGrid()
            invalidate()
            return true
        }

        override fun onLongPress(motionEvent: MotionEvent) {}


        override fun onFling(motionEvent: MotionEvent, motionEvent1: MotionEvent, v: Float, v1: Float): Boolean = false

    }

    private val scaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val overScale = measureOverScale()
            val scale = applyOverScaleFix(detector.scaleFactor, overScale)

            scaleFocusX = detector.focusX
            scaleFocusY = detector.focusY

            setScaleKeepingFocus(viewportScale * scale, scaleFocusX, scaleFocusY)

            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

        override fun onScaleEnd(detector: ScaleGestureDetector) {}

    }

    private val maximumAllowedScale: Float
        get() {
            val maximumAllowedWidth = imageRawWidth.toFloat()
            val maximumAllowedHeight = imageRawHeight.toFloat()

            return Math.min(maximumAllowedWidth / displayWidth.toFloat(), maximumAllowedHeight / displayHeight.toFloat())
        }

    private val minimumAllowedScale: Float
        get() = drawableScaleToFitWithValidRatio

    private val mSettleAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Float

        getDisplayDrawableBounds(viewportBounds)

        val overScrollX = measureOverScrollX(viewportBounds)
        val overScrollY = measureOverScrollY(viewportBounds)
        val overScale = measureOverScale()

        translateLeft -= overScrollX * animatedValue
        translateTop -= overScrollY * animatedValue

        val targetScale = viewportScale / overScale
        val newScale = (1 - animatedValue) * viewportScale + animatedValue * targetScale

        setScaleKeepingFocus(newScale, scaleFocusX, scaleFocusY)

        updateGrid()
        invalidate()
    }

    interface CropBitmapListener {
        fun onBitmapReady(bitmap: Bitmap)
        fun onError(ex: Exception?)
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

    @Suppress("UNUSED")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun initialize(
            context: Context,
            @Suppress("UNUSED_PARAMETER") attrs: AttributeSet?,
            @Suppress("UNUSED_PARAMETER") defStyleAttr: Int,
            @Suppress("UNUSED_PARAMETER") defStyleRes: Int) {

        gestureDetector = GestureDetector(context, gestureListener)
        scaleGestureDetector = ScaleGestureDetector(context, scaleGestureListener)

        maximumOverScroll = resources.displayMetrics.density * MAXIMUM_OVER_SCROLL

        animator = ValueAnimator()
        animator?.duration = SET_BACK_DURATION
        animator?.setFloatValues(0f, 1f)
        animator?.interpolator = DecelerateInterpolator(0.25f)
        animator?.addUpdateListener(mSettleAnimatorUpdateListener)

        viewportGrid.callback = gridCallback
    }

    fun setImageUri(uri: Uri) {
        cancelMakingDrawableProcessIfExists()
        imageRawWidth = 0
        imageRawHeight = 0
        uriReference = uri
        viewportImage = null
        requestLayout()
        invalidate()

        scaleDrawableToFitWithinViewWithValidRatio()

        placeDrawableInTheCenter()

        updateGrid()
    }

    fun cropSquare(callback: CropBitmapListener) {
        val width = displayWidth
        val scale = viewportScale
        val visibleSize = (width / scale).toInt()
        val size = View.MeasureSpec.makeMeasureSpec(visibleSize, View.MeasureSpec.EXACTLY)
        crop(size, size, callback)
    }

    fun crop(widthSpec: Int, heightSpec: Int, callback: CropBitmapListener) {
        val uri = uriReference
        val drawable = viewportImage
        if (uri == null && drawable == null) {
            throw IllegalStateException("Image uri is not set.")
        }

        if (drawable == null || animator?.isRunning == true) {
            postDelayed({ crop(widthSpec, heightSpec, callback) }, SET_BACK_DURATION / 2)
            return
        }

        val gridBounds = RectF(viewportGrid.bounds)
        gridBounds.offset(-translateLeft, -translateTop)

        getDisplayDrawableBounds(viewportBounds)

        val leftRatio = gridBounds.left / viewportBounds.width()
        val topRatio = gridBounds.top / viewportBounds.height()
        val rightRatio = gridBounds.right / viewportBounds.width()
        val bottomRatio = gridBounds.bottom / viewportBounds.height()

        val sampledRight = (rightRatio * imageRawWidth).toInt()
        val sampledBottom = (bottomRatio * imageRawHeight).toInt()

        val actualLeft = Math.max(0, (leftRatio * imageRawWidth).toInt())
        val actualTop = Math.max(0, (topRatio * imageRawHeight).toInt())
        val actualRight = Math.min(imageRawWidth, sampledRight)
        val actualBottom = Math.min(imageRawHeight, sampledBottom)

        val context = context

        val task = when (uri) {
            null -> CropDrawableTask(context, drawable, MINIMUM_RATIO, MAXIMUM_RATIO, actualRight, actualLeft, actualBottom, actualTop, widthSpec, heightSpec, callback)
            else -> CropImageUriTask(context, uri, MINIMUM_RATIO, MAXIMUM_RATIO, actualRight, actualLeft, actualBottom, actualTop, widthSpec, heightSpec, callback)
        }

        task.execute()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        val (targetWidth, targetHeight) = when (widthMode) {
            View.MeasureSpec.EXACTLY -> {
                val height: Int = when (heightMode) {
                    View.MeasureSpec.EXACTLY -> heightSize
                    View.MeasureSpec.AT_MOST -> Math.min(heightSize, (widthSize / DEFAULT_RATIO).toInt())
                    View.MeasureSpec.UNSPECIFIED -> (widthSize / DEFAULT_RATIO).toInt()
                    else -> (widthSize / DEFAULT_RATIO).toInt()
                }

                widthSize to height
            }
            View.MeasureSpec.AT_MOST -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> Math.min(widthSize, (heightSize * DEFAULT_RATIO).toInt()) to heightSize
                View.MeasureSpec.AT_MOST -> {
                    val specRatio = widthSize.toFloat() / heightSize.toFloat()
                    when {
                        specRatio == DEFAULT_RATIO -> widthSize to heightSize
                        specRatio > DEFAULT_RATIO -> (heightSize * DEFAULT_RATIO).toInt() to heightSize
                        else -> widthSize to (widthSize / DEFAULT_RATIO).toInt()
                    }
                }
                View.MeasureSpec.UNSPECIFIED -> widthSize to (widthSize / DEFAULT_RATIO).toInt()
                else -> widthSize to (widthSize / DEFAULT_RATIO).toInt()
            }
            View.MeasureSpec.UNSPECIFIED -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> (heightSize * DEFAULT_RATIO).toInt() to heightSize
                View.MeasureSpec.AT_MOST -> (heightSize * DEFAULT_RATIO).toInt() to heightSize
                View.MeasureSpec.UNSPECIFIED -> maximumOverScroll.toInt() to maximumOverScroll.toInt()
                else -> maximumOverScroll.toInt() to maximumOverScroll.toInt()
            }
            else -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> (heightSize * DEFAULT_RATIO).toInt() to heightSize
                View.MeasureSpec.AT_MOST -> (heightSize * DEFAULT_RATIO).toInt() to heightSize
                View.MeasureSpec.UNSPECIFIED -> maximumOverScroll.toInt() to maximumOverScroll.toInt()
                else -> maximumOverScroll.toInt() to maximumOverScroll.toInt()
            }
        }

        setMeasuredDimension(targetWidth, targetHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        displayWidth = right - left
        displayHeight = bottom - top

        if (displayWidth == 0 || displayHeight == 0) {
            return
        }

        if (uriReference == null && viewportImage == null) {
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
        if (viewportImage == null) {

            return false
        }

        val drawableWidth = viewportImage?.intrinsicWidth ?: return false
        val drawableHeight = viewportImage?.intrinsicHeight ?: return false

        return isSizeSuitableForView(drawableWidth, drawableHeight)
    }

    private fun cancelMakingDrawableProcessIfExists() {
        makeDrawableTask?.cancel(true)
        makeDrawableTask = null
    }

    private fun drawableBeingMadeIsSuitableForView(): Boolean {
        val width = makeDrawableTask?.targetWidth ?: return false
        val height = makeDrawableTask?.targetHeight ?: return false
        return isSizeSuitableForView(width, height)
    }

    private fun isSizeSuitableForView(width: Int, height: Int): Boolean {
        val viewArea = displayWidth * displayHeight
        val drawableArea = width * height
        val areaRatio = viewArea.toFloat() / drawableArea.toFloat()
        return areaRatio in 0.5f..2f
    }

    private fun startMakingSuitableDrawable() {
        val uri = uriReference
        val drawable = viewportImage

        if (uri != null) {
            val listener = object : MakeSuitableTask.SuitableDrawableListener {
                override fun onSuccess(drawable: Drawable, rawWidth: Int, rawHeight: Int) {
                    viewportImage = drawable
                    imageRawWidth = rawWidth
                    imageRawHeight = rawHeight
                    onDrawableChanged()
                }

                override fun onError(ex: Exception?) {
                    Timber.e(ex, "Could not process image for suitability")
                }
            }
            makeDrawableTask = MakeSuitableTask(context, uri, displayWidth, displayHeight, listener)
            makeDrawableTask?.execute()
        } else if (drawable != null) {
            imageRawWidth = drawable.intrinsicWidth
            imageRawHeight = drawable.intrinsicHeight

            onDrawableChanged()
        }
    }

    private fun onDrawableChanged() {
        reset()
    }

    private fun reset() {
        if (animator?.isRunning == true) {
            animator?.cancel()
        }

        scaleDrawableToFitWithinViewWithValidRatio()

        placeDrawableInTheCenter()

        updateGrid()

        invalidate()
    }

    private fun isImageSizeRatioValid(imageSizeRatio: Float): Boolean = imageSizeRatio in MINIMUM_RATIO..MAXIMUM_RATIO

    private fun scaleDrawableToFitWithinViewWithValidRatio() {
        val scale = drawableScaleToFitWithValidRatio

        setDrawableScale(scale)
    }

    private fun setDrawableScale(scale: Float) {
        viewportScale = scale

        invalidate()
    }

    private fun placeDrawableInTheCenter() {
        translateLeft = (displayWidth - displayDrawableWidth) / 2
        translateTop = (displayHeight - displayDrawableHeight) / 2

        invalidate()
    }

    private fun updateGrid() {
        getDisplayDrawableBounds(viewportBounds)

        viewportBounds.intersect(0f, 0f, displayWidth.toFloat(), displayHeight.toFloat())

        val gridLeft = viewportBounds.left
        val gridTop = viewportBounds.top

        val gridWidth = viewportBounds.width()
        val gridHeight = viewportBounds.height()

        viewportBounds.set(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)
        setGridBounds(viewportBounds)

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
        viewportGrid.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())

        invalidate()
    }

    private fun getDisplayDrawableBounds(bounds: RectF) {
        bounds.left = translateLeft
        bounds.top = translateTop
        bounds.right = bounds.left + displayDrawableWidth
        bounds.bottom = bounds.top + displayDrawableHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewportImage == null) {

            return
        }

        getDisplayDrawableBounds(viewportBounds)

        viewportImage?.setBounds(viewportBounds.left.toInt(), viewportBounds.top.toInt(), viewportBounds.right.toInt(), viewportBounds.bottom.toInt())
        viewportImage?.draw(canvas)

        viewportGrid.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (viewportImage == null) {

            return false
        }

        gestureDetector?.onTouchEvent(event)
        scaleGestureDetector?.onTouchEvent(event)

        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
            animator?.start()
        }

        return true
    }

    private fun measureOverScrollX(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.width() <= displayWidth

        if (drawableIsSmallerThanView) {
            return displayDrawableBounds.centerX() - displayWidth / 2
        }

        if (displayDrawableBounds.left <= 0 && displayDrawableBounds.right >= displayWidth) {
            return 0f
        }

        if (displayDrawableBounds.left < 0) {
            return displayDrawableBounds.right - displayWidth
        }

        return if (displayDrawableBounds.right > displayWidth) {
            displayDrawableBounds.left
        } else 0f

    }

    private fun measureOverScrollY(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.height() < displayHeight

        if (drawableIsSmallerThanView) {
            return displayDrawableBounds.centerY() - displayHeight / 2
        }

        if (displayDrawableBounds.top <= 0 && displayDrawableBounds.bottom >= displayHeight) {
            return 0f
        }

        if (displayDrawableBounds.top < 0) {
            return displayDrawableBounds.bottom - displayHeight
        }

        return if (displayDrawableBounds.bottom > displayHeight) {
            displayDrawableBounds.top
        } else 0f

    }

    private fun applyOverScrollFix(distance: Float, overScroll: Float): Float {
        if (overScroll * distance <= 0) {
            return distance
        }

        val offRatio = Math.abs(overScroll) / maximumOverScroll

        return distance - (distance * Math.sqrt(offRatio.toDouble())).toFloat()
    }

    private fun measureOverScale(): Float {
        var maximumAllowedScale = maximumAllowedScale
        val minimumAllowedScale = minimumAllowedScale

        if (maximumAllowedScale < minimumAllowedScale) {
            maximumAllowedScale = minimumAllowedScale
        }

        return when {
            viewportScale < minimumAllowedScale -> viewportScale / minimumAllowedScale
            viewportScale > maximumAllowedScale -> viewportScale / maximumAllowedScale
            else -> 1f
        }
    }

    private fun applyOverScaleFix(scale: Float, overScale: Float): Float {
        if (overScale == 1f) {
            return scale
        }

        val adjustedOverScale = if (overScale > 1) {
            1f / overScale
        } else {
            overScale
        }

        val ratio = (adjustedOverScale - MAXIMUM_OVER_SCALE) / (1 - MAXIMUM_OVER_SCALE)

        val wentOverScaleRatio = when {
            ratio < 0 -> 0f
            else -> ratio
        }

        return scale * wentOverScaleRatio + (1 - wentOverScaleRatio) / scale
    }

    private fun setScaleKeepingFocus(scale: Float, focusX: Float, focusY: Float) {
        getDisplayDrawableBounds(viewportBounds)

        val focusRatioX = (focusX - viewportBounds.left) / viewportBounds.width()
        val focusRatioY = (focusY - viewportBounds.top) / viewportBounds.height()

        viewportScale = scale

        getDisplayDrawableBounds(viewportBounds)

        val scaledFocusX = viewportBounds.left + focusRatioX * viewportBounds.width()
        val scaledFocusY = viewportBounds.top + focusRatioY * viewportBounds.height()

        translateLeft += focusX - scaledFocusX
        translateTop += focusY - scaledFocusY

        updateGrid()
        invalidate()
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {

        // TODO
    }

    override fun getSize(cb: SizeReadyCallback?) {
        cb?.onSizeReady(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
    }

    override fun getRequest(): Request? = tag as? Request

    override fun removeCallback(cb: SizeReadyCallback?) {
        // TODO
    }

    override fun setRequest(request: Request?) {
        tag = request
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        // TODO
    }

    override fun onStop() {
        // TODO
    }

    override fun onStart() {
        // TODO
    }

    override fun onResourceReady(resource: Drawable?, transition: Transition<in Drawable>?) {
        setDrawable(resource)
    }

    fun setDrawable(resource: Drawable?) {
        cancelMakingDrawableProcessIfExists()
        uriReference = null
        viewportImage = resource
        requestLayout()
        invalidate()
        scaleDrawableToFitWithinViewWithValidRatio()
        placeDrawableInTheCenter()
        updateGrid()
    }

    override fun onLoadStarted(placeholder: Drawable?) {
        // TODO
    }

    override fun onDestroy() {
        // TODO
    }
}
