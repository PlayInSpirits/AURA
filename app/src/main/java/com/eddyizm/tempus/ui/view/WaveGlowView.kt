package com.eddyizm.tempus.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The wave is drawn from small, textured masks stretched over deforming meshes. */
class WaveGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var playing = false
        set(value) {
            if (field == value) return
            field = value
            updateAnimation()
        }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val vertices = FloatArray((MESH_WIDTH + 1) * (MESH_HEIGHT + 1) * 2)
    private var masks: Array<Bitmap>? = null
    private var softness = -1f
    private val palette = WavePaletteTransition(DEFAULT)
    private val filters = Array(DEFAULT.size) { filter(DEFAULT[it]) }
    private val filteredColors = DEFAULT.copyOf()
    private var renderingEnabled = false
    private var motionEnabled = false
    private var running = false
    private var phase = 0f
    private var playbackAmount = 0f
    private var skipEnergy = 0f
    private var previousFrame = 0L

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val elapsed = if (previousFrame == 0L) 0f
                else ((frameTimeNanos - previousFrame) / 1e9f).coerceIn(0f, .08f)
            previousFrame = frameTimeNanos
            val target = if (playing) 1f else 0f
            playbackAmount += (target - playbackAmount) * (1f - exp(-elapsed * 3.5f))
            if (abs(target - playbackAmount) < .003f) playbackAmount = target
            phase = (phase + elapsed * (.52f * playbackAmount + .95f * skipEnergy)) % FULL_TURN
            skipEnergy *= exp(-elapsed * 2f)
            if (skipEnergy < .005f) skipEnergy = 0f
            palette.advance(elapsed)
            invalidate()

            if (hasMotion()) {
                Choreographer.getInstance().postFrameCallbackDelayed(this, FRAME_DELAY_MS)
            } else {
                stopAnimation()
            }
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun skip() {
        if (!playing) return
        skipEnergy = 1f
        updateAnimation()
    }

    fun setCover(bitmap: Bitmap?) {
        val colors = bitmap?.let { runCatching { paletteFromBitmap(it) }.getOrNull() }
        val next = colors?.let { IntArray(it.size) { index -> color(it[index]) } } ?: DEFAULT
        palette.retarget(next)
        if (renderingEnabled && !motionEnabled) palette.finish()
        updateAnimation()
        invalidate()
    }

    fun setRenderingEnabled(enabled: Boolean) {
        if (renderingEnabled == enabled) return
        renderingEnabled = enabled
        if (enabled) {
            motionEnabled = AuraAppearance.softWave(context)
            val newSoftness = AuraAppearance.softness(context)
            if (softness != newSoftness) {
                softness = newSoftness
                masks = null
            }
            if (!motionEnabled) {
                palette.finish()
                playbackAmount = 0f
                skipEnergy = 0f
            }
        }
        updateAnimation()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) updateAnimation() else stopAnimation()
    }

    private fun hasMotion() = playing || playbackAmount > 0f || skipEnergy > 0f || palette.isTransitioning

    private fun updateAnimation() {
        val shouldAnimate = renderingEnabled && motionEnabled && hasMotion() &&
            isAttachedToWindow && isShown && windowVisibility == VISIBLE
        if (shouldAnimate == running) return
        if (shouldAnimate) {
            running = true
            previousFrame = 0L
            Choreographer.getInstance().postFrameCallback(frame)
        } else {
            stopAnimation()
        }
    }

    private fun stopAnimation() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onDraw(canvas: Canvas) {
        if (!renderingEnabled || width == 0 || height == 0) return
        val textures = masks ?: Array(DEFAULT.size) { createMask(it) }.also { masks = it }
        val span = width.toFloat()
        val tall = min(height.toFloat(), span * 2.25f)
        val x = span * .5f
        val y = height * .48f
        val size = .9f + .1f * playbackAmount

        drawLayer(canvas, textures[2], 2, x, y, span * .88f * size, tall * .48f * size, 194)
        drawLayer(canvas, textures[1], 1, x, y, span * .85f * size, tall * .36f * size, 228)
        drawLayer(canvas, textures[0], 0, x, y, span * .76f * size, tall * .31f * size, 244)
    }

    private fun drawLayer(
        canvas: Canvas, texture: Bitmap, index: Int,
        x: Float, y: Float, radiusX: Float, radiusY: Float, opacity: Int,
    ) {
        val angle = phase + index * 2.2f
        val centerX = x + radiusX * .14f * sin(angle)
        val centerY = y + radiusY * .15f * sin(angle - 1f)
        var offset = 0
        for (row in 0..MESH_HEIGHT) {
            val v = (2f * row / MESH_HEIGHT) - 1f
            val lateral = .18f * sin(v * 3.6f + angle)
            for (column in 0..MESH_WIDTH) {
                val u = (2f * column / MESH_WIDTH) - 1f
                // Pin each edge of the mesh so adjacent triangles cannot spill beyond the mask.
                vertices[offset++] = centerX + radiusX * (u + (1f - u * u) * lateral)
                vertices[offset++] = centerY + radiusY * (v + (1f - v * v) *
                    (.24f * sin(u * 2.9f - angle) + .08f * sin(u * 6f + v + angle)))
            }
        }
        val tint = palette.colors[index]
        if (tint != filteredColors[index]) {
            filteredColors[index] = tint
            filters[index] = filter(tint)
        }
        paint.alpha = opacity
        paint.colorFilter = filters[index]
        canvas.drawBitmapMesh(texture, MESH_WIDTH, MESH_HEIGHT, vertices, 0, null, 0, paint)
    }

    /** Soft folds are baked into the masks, so only their meshes and tints are updated during playback. */
    private fun createMask(layer: Int): Bitmap {
        val pixels = IntArray(MASK_SIZE * MASK_SIZE)
        val seed = layer * 2.3f
        val feather = .65f + softness * .26f
        for (row in 0 until MASK_SIZE) {
            val y = 2f * row / (MASK_SIZE - 1) - 1f
            for (column in 0 until MASK_SIZE) {
                val x = 2f * column / (MASK_SIZE - 1) - 1f
                val u = x + .13f * sin(y * 3.4f + seed)
                val v = y + .17f * sin(x * 3.7f - seed) + .06f * sin(x * 7f + y * 4f)
                val distance = sqrt(u * u + v * v)
                val edge = smooth((1f - distance) / feather) *
                    smooth((1f - max(abs(x), abs(y))) * 8f)
                val fold = v - .25f * sin(u * 3.1f + seed) - u * .18f
                val secondFold = v + .42f - .22f * sin(u * 3.9f - seed)
                val crest = ridge(fold + .025f, 66f)
                val shadow = ridge(fold - .11f, 33f)
                val reflected = ridge(secondFold, 90f)
                val depth = if (layer == 2) .4f else 1f
                val light = .82f + depth * (.33f * crest - .42f * shadow + .18f * reflected)
                val alpha = (edge * light * 255f).toInt().coerceIn(0, 255)
                pixels[row * MASK_SIZE + column] = (alpha shl 24) or 0x00FFFFFF
            }
        }
        return Bitmap.createBitmap(pixels, MASK_SIZE, MASK_SIZE, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val MASK_SIZE = 128
        private const val MESH_WIDTH = 10
        private const val MESH_HEIGHT = 10
        private const val FRAME_DELAY_MS = 25L
        private const val FULL_TURN = 6.2831855f
        private val DEFAULT = intArrayOf(0xFFA680F2.toInt(), 0xFF6136BD.toInt(), 0xFF3B579E.toInt())

        private fun smooth(amount: Float): Float {
            val t = amount.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun ridge(offset: Float, sharpness: Float) = exp(-offset * offset * sharpness)

        private fun color(components: FloatArray) =
            Color.rgb((components[0] * 255).toInt(), (components[1] * 255).toInt(), (components[2] * 255).toInt())

        private fun filter(color: Int) = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        private fun hsl2rgb(h: Float, s: Float, l: Float): FloatArray {
            val a = s * min(l, 1 - l)
            fun f(n: Float): Float {
                val k = (n + h / 30f) % 12f
                return l - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
            }
            return floatArrayOf(f(0f), f(8f), f(4f))
        }

        /** Three vivid colours (strongest hues by saturation × brightness, ≥ 40° apart), or null for a grey cover. */
        fun paletteFromBitmap(src: Bitmap): Array<FloatArray>? {
            val bmp = if (src.width > 32 || src.height > 32) Bitmap.createScaledBitmap(src, 24, 24, true) else src
            val bins = 12
            val weight = FloatArray(bins)
            var total = 0f
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val hsv = FloatArray(3)
            for (c in px) {
                if (Color.alpha(c) < 128) continue
                Color.colorToHSV(c, hsv)
                val w = hsv[1] * hsv[1] * hsv[2]
                weight[(floor(hsv[0] / (360f / bins)).toInt()).coerceIn(0, bins - 1)] += w
                total += w
            }
            if (total < 1f) return null
            val order = (0 until bins).sortedByDescending { weight[it] }
            val hues = mutableListOf((order[0] + 0.5f) * (360f / bins))
            for (i in order.drop(1)) {
                if (hues.size >= 3 || weight[i] < weight[order[0]] * 0.25f) break
                val hue = (i + 0.5f) * (360f / bins)
                if (hues.all { val dd = kotlin.math.abs(it - hue); min(dd, 360f - dd) >= 40f }) hues.add(hue)
            }
            while (hues.size < 3) hues.add((hues[0] + (if (hues.size == 1) 45f else -45f) + 360f) % 360f)
            return arrayOf(
                hsl2rgb(hues[0], 0.92f, 0.58f),
                hsl2rgb(hues[1], 0.85f, 0.52f),
                hsl2rgb(hues[2], 0.85f, 0.46f),
            )
        }
    }
}
