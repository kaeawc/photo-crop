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

        const val DEFAULT_MINIMUM_RATIO = 5f / 8f
        const val DEFAULT_MAXIMUM_RATIO = 1.9f
        const val DEFAULT_RATIO = 1f

        private const val MAXIMUM_OVER_SCROLL = 144f
        private const val MAXIMUM_OVER_SCALE = 0.7f

        private const val SET_BACK_DURATION: Long = 400
    }

    private var mMinimumRatio = DEFAULT_MINIMUM_RATIO
    private var mMaximumRatio = DEFAULT_MAXIMUM_RATIO
    private var mDefaultRatio = DEFAULT_RATIO

    private var mImageUri: Uri? = null
    private var mImageRawWidth: Int = 0

    private var mImageRawHeight: Int = 0

    private var mMakeDrawableTask: MakeSuitableTask? = null

    private var mWidth: Int = 0
    private var mHeight: Int = 0

    private val mGridDrawable = GridDrawable()

    private var mDrawable: Drawable? = null

    private var mDrawableScale: Float = 0f
    private var mScaleFocusX: Float = 0f
    private var mScaleFocusY: Float = 0f

    private var mDisplayDrawableLeft: Float = 0f
    private var mDisplayDrawableTop: Float = 0f

    private val mHelperRect = RectF()

    private var mGestureDetector: GestureDetector? = null
    private var mScaleGestureDetector: ScaleGestureDetector? = null

    private var mMaximumOverScroll: Float = 0f

    private var mAnimator: ValueAnimator? = null

    private val mGridCallback = object : Drawable.Callback {

        override fun invalidateDrawable(who: Drawable) {
            invalidate()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
    }

    private val isMakingDrawableForView: Boolean
        get() = mMakeDrawableTask != null

    private val imageSizeRatio: Float
        get() = mImageRawWidth.toFloat() / mImageRawHeight.toFloat()

    private val drawableScaleToFitWithValidRatio: Float
        get() {
            val drawableSizeRatio = imageSizeRatio
            val imageSizeRatioIsValid = isImageSizeRatioValid(drawableSizeRatio)
            val height = mHeight.toFloat()
            val width = mWidth.toFloat()
            val rawWidth = mImageRawWidth.toFloat()
            val rawHeight = mImageRawHeight.toFloat()

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
                if (drawableSizeRatio < mMaximumRatio) {
                    getBoundsForWidthAndRatio(rawWidth, mMinimumRatio, mHelperRect)
                    mHelperRect.height() / height
                } else {
                    getBoundsForHeightAndRatio(rawHeight, mMaximumRatio, mHelperRect)
                    mHelperRect.width() / width
                }
            } else {
                if (drawableSizeRatio < mMinimumRatio) {
                    getBoundsForHeightAndRatio(height, mMinimumRatio, mHelperRect)
                    mHelperRect.width() / rawWidth
                } else {
                    getBoundsForWidthAndRatio(width, mMaximumRatio, mHelperRect)
                    mHelperRect.height() / rawHeight
                }
            }
        }

    private val displayDrawableWidth: Float
        get() = mDrawableScale * mImageRawWidth

    private val displayDrawableHeight: Float
        get() = mDrawableScale * mImageRawHeight

    private val mOnGestureListener = object : GestureDetector.OnGestureListener {

        override fun onDown(motionEvent: MotionEvent): Boolean = true

        override fun onShowPress(motionEvent: MotionEvent) {}

        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean = false

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            getDisplayDrawableBounds(mHelperRect)
            val overScrollX = measureOverScrollX(mHelperRect)
            val overScrollY = measureOverScrollY(mHelperRect)
            mDisplayDrawableLeft += applyOverScrollFix(-distanceX, overScrollX)
            mDisplayDrawableTop += applyOverScrollFix(-distanceY, overScrollY)
            updateGrid()
            invalidate()
            return true
        }

        override fun onLongPress(motionEvent: MotionEvent) {}


        override fun onFling(motionEvent: MotionEvent, motionEvent1: MotionEvent, v: Float, v1: Float): Boolean = false

    }

    private val mOnScaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val overScale = measureOverScale()
            val scale = applyOverScaleFix(detector.scaleFactor, overScale)

            mScaleFocusX = detector.focusX
            mScaleFocusY = detector.focusY

            setScaleKeepingFocus(mDrawableScale * scale, mScaleFocusX, mScaleFocusY)

            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

        override fun onScaleEnd(detector: ScaleGestureDetector) {}

    }

    private val maximumAllowedScale: Float
        get() {
            val maximumAllowedWidth = mImageRawWidth.toFloat()
            val maximumAllowedHeight = mImageRawHeight.toFloat()

            return Math.min(maximumAllowedWidth / mWidth.toFloat(), maximumAllowedHeight / mHeight.toFloat())
        }

    private val minimumAllowedScale: Float
        get() = drawableScaleToFitWithValidRatio

    private val mSettleAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Float

        getDisplayDrawableBounds(mHelperRect)

        val overScrollX = measureOverScrollX(mHelperRect)
        val overScrollY = measureOverScrollY(mHelperRect)
        val overScale = measureOverScale()

        mDisplayDrawableLeft -= overScrollX * animatedValue
        mDisplayDrawableTop -= overScrollY * animatedValue

        val targetScale = mDrawableScale / overScale
        val newScale = (1 - animatedValue) * mDrawableScale + animatedValue * targetScale

        setScaleKeepingFocus(newScale, mScaleFocusX, mScaleFocusY)

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

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        mGestureDetector = GestureDetector(context, mOnGestureListener)
        mScaleGestureDetector = ScaleGestureDetector(context, mOnScaleGestureListener)

        mMaximumOverScroll = resources.displayMetrics.density * MAXIMUM_OVER_SCROLL

        mAnimator = ValueAnimator()
        mAnimator?.duration = SET_BACK_DURATION
        mAnimator?.setFloatValues(0f, 1f)
        mAnimator?.interpolator = DecelerateInterpolator(0.25f)
        mAnimator?.addUpdateListener(mSettleAnimatorUpdateListener)

        mGridDrawable.callback = mGridCallback
    }

    fun setRatios(defaultRatio: Float, minimumRatio: Float, maximumRatio: Float) {
        mDefaultRatio = defaultRatio
        mMinimumRatio = minimumRatio
        mMaximumRatio = maximumRatio

        if (mAnimator?.isRunning == true) {
            mAnimator?.cancel()
        }

        cancelMakingDrawableProcessIfExists()

        mDrawable = null

        requestLayout()
    }

    fun setImageUri(uri: Uri) {
        cancelMakingDrawableProcessIfExists()
        mImageRawWidth = 0
        mImageRawHeight = 0
        mImageUri = uri
        mDrawable = null
        requestLayout()
        invalidate()

        scaleDrawableToFitWithinViewWithValidRatio()

        placeDrawableInTheCenter()

        updateGrid()
    }

    fun cropSquare(callback: CropBitmapListener) {
        val width = mWidth
        Timber.i("width: $width")
        val scale = mDrawableScale
        Timber.i("scale: $scale")
        val visibleSize = (width / scale).toInt()
        Timber.i("visibleSize: $visibleSize")
        val size = View.MeasureSpec.makeMeasureSpec(visibleSize, View.MeasureSpec.EXACTLY)
        Timber.i("cropSquare to size $visibleSize")
        crop(size, size, callback)
    }

    fun crop(widthSpec: Int, heightSpec: Int, callback: CropBitmapListener) {
        val uri = mImageUri
        val drawable = mDrawable
        if (uri == null && drawable == null) {
            throw IllegalStateException("Image uri is not set.")
        }

        if (drawable == null || mAnimator?.isRunning == true) {
            postDelayed({ crop(widthSpec, heightSpec, callback) }, SET_BACK_DURATION / 2)
            return
        }

        val gridBounds = RectF(mGridDrawable.bounds)
        gridBounds.offset(-mDisplayDrawableLeft, -mDisplayDrawableTop)

        getDisplayDrawableBounds(mHelperRect)

        val leftRatio = gridBounds.left / mHelperRect.width()
        val topRatio = gridBounds.top / mHelperRect.height()
        val rightRatio = gridBounds.right / mHelperRect.width()
        val bottomRatio = gridBounds.bottom / mHelperRect.height()

        val sampledRight = (rightRatio * mImageRawWidth).toInt()
        val sampledBottom = (bottomRatio * mImageRawHeight).toInt()

        val actualLeft = Math.max(0, (leftRatio * mImageRawWidth).toInt())
        val actualTop = Math.max(0, (topRatio * mImageRawHeight).toInt())
        val actualRight = Math.min(mImageRawWidth, sampledRight)
        val actualBottom = Math.min(mImageRawHeight, sampledBottom)

        val context = context

        val task = when (uri) {
            null -> CropDrawableTask(context, drawable, mMinimumRatio, mMaximumRatio, actualRight, actualLeft, actualBottom, actualTop, widthSpec, heightSpec, callback)
            else -> CropImageUriTask(context, uri, mMinimumRatio, mMaximumRatio, actualRight, actualLeft, actualBottom, actualTop, widthSpec, heightSpec, callback)
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
                    View.MeasureSpec.AT_MOST -> Math.min(heightSize, (widthSize / mDefaultRatio).toInt())
                    View.MeasureSpec.UNSPECIFIED -> (widthSize / mDefaultRatio).toInt()
                    else -> (widthSize / mDefaultRatio).toInt()
                }

                widthSize to height
            }
            View.MeasureSpec.AT_MOST -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> Math.min(widthSize, (heightSize * mDefaultRatio).toInt()) to heightSize
                View.MeasureSpec.AT_MOST -> {
                    val specRatio = widthSize.toFloat() / heightSize.toFloat()
                    when {
                        specRatio == mDefaultRatio -> widthSize to heightSize
                        specRatio > mDefaultRatio -> (heightSize * mDefaultRatio).toInt() to heightSize
                        else -> widthSize to (widthSize / mDefaultRatio).toInt()
                    }
                }
                View.MeasureSpec.UNSPECIFIED -> widthSize to (widthSize / mDefaultRatio).toInt()
                else -> widthSize to (widthSize / mDefaultRatio).toInt()
            }
            View.MeasureSpec.UNSPECIFIED -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> (heightSize * mDefaultRatio).toInt() to heightSize
                View.MeasureSpec.AT_MOST -> (heightSize * mDefaultRatio).toInt() to heightSize
                View.MeasureSpec.UNSPECIFIED -> mMaximumOverScroll.toInt() to mMaximumOverScroll.toInt()
                else -> mMaximumOverScroll.toInt() to mMaximumOverScroll.toInt()
            }
            else -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> (heightSize * mDefaultRatio).toInt() to heightSize
                View.MeasureSpec.AT_MOST -> (heightSize * mDefaultRatio).toInt() to heightSize
                View.MeasureSpec.UNSPECIFIED -> mMaximumOverScroll.toInt() to mMaximumOverScroll.toInt()
                else -> mMaximumOverScroll.toInt() to mMaximumOverScroll.toInt()
            }
        }

        setMeasuredDimension(targetWidth, targetHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mWidth = right - left
        mHeight = bottom - top

        if (mWidth == 0 || mHeight == 0) {
            return
        }

        if (mImageUri == null && mDrawable == null) {
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
        if (mDrawable == null) {

            return false
        }

        val drawableWidth = mDrawable?.intrinsicWidth ?: return false
        val drawableHeight = mDrawable?.intrinsicHeight ?: return false

        return isSizeSuitableForView(drawableWidth, drawableHeight)
    }

    private fun cancelMakingDrawableProcessIfExists() {
        mMakeDrawableTask?.cancel(true)
        mMakeDrawableTask = null
    }

    private fun drawableBeingMadeIsSuitableForView(): Boolean {
        val width = mMakeDrawableTask?.targetWidth ?: return false
        val height = mMakeDrawableTask?.targetHeight ?: return false
        return isSizeSuitableForView(width, height)
    }

    private fun isSizeSuitableForView(width: Int, height: Int): Boolean {
        val viewArea = mWidth * mHeight
        val drawableArea = width * height
        val areaRatio = viewArea.toFloat() / drawableArea.toFloat()
        return areaRatio in 0.5f..2f
    }

    private fun startMakingSuitableDrawable() {
        val uri = mImageUri
        val drawable = mDrawable

        if (uri != null) {
            mMakeDrawableTask = object : MakeSuitableTask(context, uri, mWidth, mHeight) {

                override fun onPostExecute(drawable: Drawable) {
                    mDrawable = drawable

                    mImageRawWidth = rawWidth
                    mImageRawHeight = rawHeight

                    onDrawableChanged()
                }

            }

            mMakeDrawableTask?.execute()
        } else if (drawable != null) {
            mImageRawWidth = drawable.intrinsicWidth
            mImageRawHeight = drawable.intrinsicHeight

            onDrawableChanged()
        }
    }

    private fun onDrawableChanged() {
        reset()
    }

    private fun reset() {
        if (mAnimator?.isRunning == true) {
            mAnimator?.cancel()
        }

        scaleDrawableToFitWithinViewWithValidRatio()

        placeDrawableInTheCenter()

        updateGrid()

        invalidate()
    }

    private fun isImageSizeRatioValid(imageSizeRatio: Float): Boolean = imageSizeRatio in mMinimumRatio..mMaximumRatio

    private fun scaleDrawableToFitWithinViewWithValidRatio() {
        val scale = drawableScaleToFitWithValidRatio

        setDrawableScale(scale)
    }

    private fun setDrawableScale(scale: Float) {
        mDrawableScale = scale

        invalidate()
    }

    private fun placeDrawableInTheCenter() {
        mDisplayDrawableLeft = (mWidth - displayDrawableWidth) / 2
        mDisplayDrawableTop = (mHeight - displayDrawableHeight) / 2

        invalidate()
    }

    private fun updateGrid() {
        getDisplayDrawableBounds(mHelperRect)

        mHelperRect.intersect(0f, 0f, mWidth.toFloat(), mHeight.toFloat())

        val gridLeft = mHelperRect.left
        val gridTop = mHelperRect.top

        val gridWidth = mHelperRect.width()
        val gridHeight = mHelperRect.height()

        mHelperRect.set(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight)
        setGridBounds(mHelperRect)

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
        mGridDrawable.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())

        invalidate()
    }

    private fun getDisplayDrawableBounds(bounds: RectF) {
        bounds.left = mDisplayDrawableLeft
        bounds.top = mDisplayDrawableTop
        bounds.right = bounds.left + displayDrawableWidth
        bounds.bottom = bounds.top + displayDrawableHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mDrawable == null) {

            return
        }

        getDisplayDrawableBounds(mHelperRect)

        mDrawable?.setBounds(mHelperRect.left.toInt(), mHelperRect.top.toInt(), mHelperRect.right.toInt(), mHelperRect.bottom.toInt())
        mDrawable?.draw(canvas)

        mGridDrawable.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mDrawable == null) {

            return false
        }

        mGestureDetector?.onTouchEvent(event)
        mScaleGestureDetector?.onTouchEvent(event)

        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
            mAnimator?.start()
        }

        return true
    }

    private fun measureOverScrollX(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.width() <= mWidth

        if (drawableIsSmallerThanView) {
            return displayDrawableBounds.centerX() - mWidth / 2
        }

        if (displayDrawableBounds.left <= 0 && displayDrawableBounds.right >= mWidth) {
            return 0f
        }

        if (displayDrawableBounds.left < 0) {
            return displayDrawableBounds.right - mWidth
        }

        return if (displayDrawableBounds.right > mWidth) {
            displayDrawableBounds.left
        } else 0f

    }

    private fun measureOverScrollY(displayDrawableBounds: RectF): Float {
        val drawableIsSmallerThanView = displayDrawableBounds.height() < mHeight

        if (drawableIsSmallerThanView) {
            return displayDrawableBounds.centerY() - mHeight / 2
        }

        if (displayDrawableBounds.top <= 0 && displayDrawableBounds.bottom >= mHeight) {
            return 0f
        }

        if (displayDrawableBounds.top < 0) {
            return displayDrawableBounds.bottom - mHeight
        }

        return if (displayDrawableBounds.bottom > mHeight) {
            displayDrawableBounds.top
        } else 0f

    }

    private fun applyOverScrollFix(distance: Float, overScroll: Float): Float {
        if (overScroll * distance <= 0) {
            return distance
        }

        val offRatio = Math.abs(overScroll) / mMaximumOverScroll

        return distance - (distance * Math.sqrt(offRatio.toDouble())).toFloat()
    }

    private fun measureOverScale(): Float {
        var maximumAllowedScale = maximumAllowedScale
        val minimumAllowedScale = minimumAllowedScale

        if (maximumAllowedScale < minimumAllowedScale) {
            maximumAllowedScale = minimumAllowedScale
        }

        return when {
            mDrawableScale < minimumAllowedScale -> mDrawableScale / minimumAllowedScale
            mDrawableScale > maximumAllowedScale -> mDrawableScale / maximumAllowedScale
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
        getDisplayDrawableBounds(mHelperRect)

        val focusRatioX = (focusX - mHelperRect.left) / mHelperRect.width()
        val focusRatioY = (focusY - mHelperRect.top) / mHelperRect.height()

        mDrawableScale = scale

        getDisplayDrawableBounds(mHelperRect)

        val scaledFocusX = mHelperRect.left + focusRatioX * mHelperRect.width()
        val scaledFocusY = mHelperRect.top + focusRatioY * mHelperRect.height()

        mDisplayDrawableLeft += focusX - scaledFocusX
        mDisplayDrawableTop += focusY - scaledFocusY

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
        Timber.i("setDrawable")
        cancelMakingDrawableProcessIfExists()
//        mImageRawWidth = resource?.intrinsicWidth ?: 0
//        mImageRawHeight = resource?.intrinsicHeight ?: 0
//        mWidth = mImageRawWidth
//        mHeight = mImageRawHeight
        setRatios(DEFAULT_RATIO, DEFAULT_MINIMUM_RATIO, DEFAULT_MAXIMUM_RATIO)
        setDrawableScale(1f)
        mImageUri = null
        mDrawable = resource
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
