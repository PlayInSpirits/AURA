package com.eddyizm.tempus.ui.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R

/**
 * AURA: paints the full-screen player's backdrop from the current cover: the cover, blurred and
 * dimmed, under a gradient of its main colour that sinks into black. A new song crossfades in.
 */
object AuraPlayerBackdrop {
    private const val BOTTOM = 0xFF050409.toInt()
    private const val DEFAULT = 0xFF3B2A7A.toInt()

    private var shownKey: String? = null
    private var shownColor = DEFAULT
    private var colorAnimator: ValueAnimator? = null

    @JvmStatic
    fun update(anyView: View?, art: Uri?) {
        val root = anyView?.rootView?.findViewById<View>(R.id.player_body_root) ?: return
        val image = root.findViewById<ImageView>(R.id.aura_player_backdrop_art) ?: return
        val tint = root.findViewById<View>(R.id.aura_player_backdrop_tint) ?: return
        val key = art?.toString()
        if (key == shownKey && tint.background != null) return
        shownKey = key
        if (tint.background == null) paintTint(tint, shownColor)

        if (art == null) {
            crossfade(image, ColorDrawable(Color.TRANSPARENT))
            animateTo(tint, DEFAULT)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && image.getTag(R.id.aura_player_backdrop_art) == null) {
            image.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP))
            image.setTag(R.id.aura_player_backdrop_art, true)
        }
        Glide.with(image).asBitmap().load(art).override(64, 64).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (shownKey != key) return
                // A tiny bitmap stretched with filtering is already soft; the blur above makes it silk.
                val copy = resource.copy(Bitmap.Config.ARGB_8888, false)
                val drawable = BitmapDrawable(image.resources, copy).apply { isFilterBitmap = true }
                crossfade(image, drawable)
                animateTo(tint, mainColor(copy))
            }

            override fun onLoadCleared(placeholder: Drawable?) {}

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (shownKey != key) return
                crossfade(image, ColorDrawable(Color.TRANSPARENT))
                animateTo(tint, DEFAULT)
            }
        })
    }

    private fun crossfade(image: ImageView, next: Drawable) {
        val current = image.drawable ?: ColorDrawable(Color.TRANSPARENT)
        val fade = TransitionDrawable(arrayOf(current, next)).apply { isCrossFadeEnabled = true }
        image.setImageDrawable(fade)
        fade.startTransition(650)
    }

    private fun animateTo(tint: View, color: Int) {
        colorAnimator?.cancel()
        val from = shownColor
        shownColor = color
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 700
            addUpdateListener { paintTint(tint, it.animatedValue as Int) }
            start()
        }
    }

    private fun paintTint(tint: View, color: Int) {
        val top = withAlpha(color, 0xB3)
        val middle = withAlpha(darken(color, 0.45f), 0xE0)
        tint.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, middle, BOTTOM))
    }

    /** The cover's most telling colour: saturated, bright pixels weigh more; kept deep enough for white text. */
    private fun mainColor(bitmap: Bitmap): Int {
        val hsv = FloatArray(3)
        var x = 0.0
        var y = 0.0
        var weightSum = 0.0
        var satSum = 0.0
        val step = maxOf(1, bitmap.width / 24)
        var i = 0
        while (i < bitmap.width) {
            var j = 0
            while (j < bitmap.height) {
                val c = bitmap.getPixel(i, j)
                Color.colorToHSV(c, hsv)
                val w = (hsv[1] * hsv[2]).toDouble() + 0.02
                val angle = Math.toRadians(hsv[0].toDouble())
                x += Math.cos(angle) * w
                y += Math.sin(angle) * w
                satSum += hsv[1] * w
                weightSum += w
                j += step
            }
            i += step
        }
        if (weightSum <= 0.0) return DEFAULT
        var hue = Math.toDegrees(Math.atan2(y, x)).toFloat()
        if (hue < 0) hue += 360f
        val sat = (satSum / weightSum).toFloat()
        if (sat < 0.12f) return 0xFF34303F.toInt() // a grey cover: a quiet grey-violet
        return Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0.45f, 0.85f), 0.62f))
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt(),
    )

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
