package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.WebtoonDoubleTapBehavior
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import kotlin.math.abs

/**
 * Implementation of a [RecyclerView] used by the webtoon reader.
 */
class WebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    var originalHeight = 0
        private set
    private var heightSet = false
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE
    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE && !fitHeightMode) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }
    private val minRate
        get() = when {
            fitHeightMode -> fitHeightScale.coerceAtLeast(MIN_RATE)
            zoomOutDisabled -> DEFAULT_RATE
            else -> MIN_RATE
        }

    private val listener = GestureListener()
    private val detector = Detector()

    var doubleTapZoom = true
    var doubleTapBehavior = WebtoonDoubleTapBehavior.ZOOM_IN

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    private var isManuallyScrolling = false
    private var tapDuringManualScroll = false

    // Fit-height state
    private var fitHeightMode = false
    private var fitHeightScrolled = false
    private var fitHeightPanelPosition = NO_POSITION
    private var fitHeightPanelHeight = 0
    private var fitHeightScale = DEFAULT_RATE

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        if (!heightSet) {
            originalHeight = MeasureSpec.getSize(heightSpec)
            heightSet = true
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            tapDuringManualScroll = isManuallyScrolling
        }

        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager
        lastVisibleItemPosition =
            (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

        if (fitHeightMode && dy != 0) {
            fitHeightScrolled = true
        }
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0

        if (state == SCROLL_STATE_IDLE) {
            isManuallyScrolling = false
        }
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1) {
            return 0f
        }
        val maxPositionX = halfWidth * (currentScale - 1)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1) {
            return (originalHeight / 2 - halfHeight).toFloat()
        }
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
        fromRate: Float,
        toRate: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float,
        onEnd: (() -> Unit)? = null,
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation -> x = animation.animatedValue as Float }

        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation -> y = animation.animatedValue as Float }

        val scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate)
        scaleAnimator.addUpdateListener { animation ->
            currentScale = animation.animatedValue as Float
            setScaleRate(currentScale)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration = ANIMATOR_DURATION_TIME.toLong()
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.doOnEnd {
            isZooming = false
            currentScale = toRate
            onEnd?.invoke()
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val distanceTimeFactor = 0.4f
        val animatorSet = AnimatorSet()

        if (velocityX != 0) {
            val dx = (distanceTimeFactor * velocityX / 2)
            val newX = getPositionX(x + dx)
            val translationXAnimator = ValueAnimator.ofFloat(x, newX)
            translationXAnimator.addUpdateListener { animation -> x = getPositionX(animation.animatedValue as Float) }
            animatorSet.play(translationXAnimator)
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val dy = (distanceTimeFactor * velocityY / 2)
            val newY = getPositionY(y + dy)
            val translationYAnimator = ValueAnimator.ofFloat(y, newY)
            translationYAnimator.addUpdateListener { animation -> y = getPositionY(animation.animatedValue as Float) }
            animatorSet.play(translationYAnimator)
        }

        animatorSet.duration = 400
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()

        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) {
            x = getPositionX(x + dx)
        }
        if (dy != 0) {
            y = getPositionY(y + dy)
        }
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scaleFactor: Float) {
        // Any manual pinch exits fit-height mode
        if (fitHeightMode) {
            fitHeightMode = false
        }

        currentScale *= scaleFactor
        currentScale = currentScale.coerceIn(
            minRate,
            MAX_SCALE_RATE,
        )

        setScaleRate(currentScale)

        layoutParams.height = if (currentScale < 1) {
            (originalHeight / currentScale).toInt()
        } else {
            originalHeight
        }
        halfHeight = layoutParams.height / 2

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }

        requestLayout()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true
        }
    }

    fun onScaleEnd() {
        if (scaleX < minRate) {
            zoom(currentScale, minRate, x, 0f, y, 0f)
        }
    }

    fun onManualScroll() {
        isManuallyScrolling = true
    }

    /**
     * Zoom out to fit the full height of the panel at the tap position.
     * Layout and scroll are set BEFORE the animation with a compensating Y translation
     * so there's no visual jump, then we animate smoothly to the target state.
     */
    private fun zoomToFitHeight(ev: MotionEvent) {
        val child = findChildViewUnder(ev.x, ev.y) ?: return
        val position = getChildAdapterPosition(child)
        if (position == NO_POSITION) return

        val panelHeight = child.height
        if (panelHeight <= 0 || panelHeight <= originalHeight) return

        val targetScale = originalHeight.toFloat() / panelHeight.toFloat()
        if (targetScale >= DEFAULT_RATE) return

        // Store panel info for zoom-back
        fitHeightPanelPosition = position
        fitHeightPanelHeight = panelHeight
        fitHeightScale = targetScale

        // Remember child's screen position before any layout changes
        val childTopBefore = child.top

        // Pre-set layout height so the full panel is laid out (needed at zoomed-out scale)
        val lm = layoutManager as? LinearLayoutManager ?: return
        layoutParams.height = (originalHeight / targetScale).toInt()
        halfHeight = layoutParams.height / 2
        lm.scrollToPositionWithOffset(position, 0)

        // Compensate: set y so that visually the panel stays in the same screen position.
        y = childTopBefore.toFloat()

        // Target Y: panel centered on screen at targetScale.
        val targetY = (originalHeight - panelHeight).toFloat() / 2f

        fitHeightMode = true
        fitHeightScrolled = false

        // Delay animation by one frame so layout settles first
        post {
            zoom(DEFAULT_RATE, targetScale, 0f, 0f, childTopBefore.toFloat(), targetY)
        }
    }

    /**
     * Zoom back from fit-height to fit-width (1x).
     * If the user hasn't scrolled: clamp to panel bounds so adjacent panels are never shown.
     * If the user has scrolled (sees adjacent panels): zoom back normally at tap position.
     */
    private fun zoomBackFromFitHeight(ev: MotionEvent) {
        fitHeightMode = false

        if (fitHeightScrolled) {
            // User scrolled while zoomed out — zoom back keeping tap point stationary
            zoomToDefault(ev)
        } else {
            // User didn't scroll — clamp to panel bounds
            val panelFraction = ev.y / originalHeight.toFloat()
            val panelY = (panelFraction * fitHeightPanelHeight).toInt()

            val scrollInPanel = panelY.minus(originalHeight / 2).coerceIn(
                0,
                (fitHeightPanelHeight - originalHeight).coerceAtLeast(0),
            )

            val savedPosition = fitHeightPanelPosition
            val endY = -scrollInPanel.toFloat()

            zoom(currentScale, DEFAULT_RATE, x, 0f, y, endY) {
                y = 0f
                layoutParams.height = originalHeight
                halfHeight = originalHeight / 2
                requestLayout()

                val lm = layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(savedPosition, -scrollInPanel)
            }
        }
    }

    /**
     * Zoom back to 1x keeping the tap point at its screen position,
     * same principle as the zoom-in double tap. Layout changes are
     * deferred to onEnd to avoid jumps when the layout is enlarged.
     */
    private fun zoomToDefault(ev: MotionEvent) {
        val tapX = ev.x
        val tapY = ev.y
        val tapScreenY = y + halfHeight + (tapY - halfHeight) * currentScale
        val toY = y + (tapY - halfHeight) * (currentScale - DEFAULT_RATE)

        zoom(currentScale, DEFAULT_RATE, x, 0f, y, toY) {
            val child = findChildViewUnder(tapX, tapY)
            val position = if (child != null) getChildAdapterPosition(child) else NO_POSITION
            val offsetInChild = if (child != null) (tapY - child.top).toInt() else 0

            y = 0f
            layoutParams.height = originalHeight
            halfHeight = originalHeight / 2
            requestLayout()

            if (position != NO_POSITION) {
                val lm = layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(position, tapScreenY.toInt() - offsetInChild)
            }
        }
    }

    inner class GestureListener : GestureDetectorWithLongTap.Listener() {

        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            if (!tapDuringManualScroll) {
                tapListener?.invoke(ev)
            }
            return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (isZooming) return

            when (doubleTapBehavior) {
                WebtoonDoubleTapBehavior.OFF -> {
                    // Double tap disabled, do nothing
                }
                WebtoonDoubleTapBehavior.ZOOM_IN -> {
                    // Original behavior (respects legacy doubleTapZoom toggle)
                    if (!doubleTapZoom) return
                    if (scaleX != DEFAULT_RATE) {
                        zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                        layoutParams.height = originalHeight
                        halfHeight = layoutParams.height / 2
                        requestLayout()
                    } else {
                        val toScale = 2f
                        val toX = (halfWidth - ev.x) * (toScale - 1)
                        val toY = (halfHeight - ev.y) * (toScale - 1)
                        zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY)
                    }
                }
                WebtoonDoubleTapBehavior.FIT_HEIGHT -> {
                    if (fitHeightMode) {
                        // Currently zoomed out to fit height -> zoom back to 1x
                        zoomBackFromFitHeight(ev)
                    } else if (scaleX != DEFAULT_RATE) {
                        // Currently at some other zoom level -> reset to 1x
                        fitHeightMode = false
                        zoomToDefault(ev)
                    } else {
                        // At 1x -> zoom out to fit panel height
                        zoomToFitHeight(ev)
                    }
                }
            }
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {

        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) {
                        return true
                    }

                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) {
                        return false
                    }

                    val x = (ev.getX(index) + 0.5f).toInt()
                    val y = (ev.getY(index) + 0.5f).toInt()
                    var dx = x - downX
                    var dy = if (atFirstPosition || atLastPosition) y - downY else 0

                    if (!isZoomDragging && currentScale > 1f) {
                        var startScroll = false

                        if (abs(dx) > touchSlop) {
                            if (dx < 0) {
                                dx += touchSlop
                            } else {
                                dx -= touchSlop
                            }
                            startScroll = true
                        }
                        if (abs(dy) > touchSlop) {
                            if (dy < 0) {
                                dy += touchSlop
                            } else {
                                dy -= touchSlop
                            }
                            startScroll = true
                        }

                        if (startScroll) {
                            isZoomDragging = true
                        }
                    }

                    if (isZoomDragging) {
                        zoomScrollBy(dx, dy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) {
                        listener.onDoubleTapConfirmed(ev)
                    }
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}

private const val ANIMATOR_DURATION_TIME = 200
private const val MIN_RATE = 0.5f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 3f
