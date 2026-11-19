package com.eddyizm.tempus.ui.view

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R

/** A quiet cover tint behind the player, sampled at thumbnail size. */
object AuraPlayerBackdrop {
    private const val BASE = 0xFF231A32.toInt()
    private const val BOTTOM = 0xFF100C18.toInt()

    @JvmStatic
    fun update(anyView: View?, art: Uri?) {
        val tint = anyView?.rootView?.findViewById<View>(R.id.aura_player_backdrop_tint) ?: return
        val key = art?.toString().orEmpty()
        if (tint.getTag(R.id.aura_player_backdrop_tint) == key) return
        tint.setTag(R.id.aura_player_backdrop_tint, key)
        if (tint.background == null || art == null) paint(tint, BASE)
        Glide.with(tint).asBitmap().load(art).override(24, 24).into(object : CustomViewTarget<View, Bitmap>(tint) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                val color = WaveGlowView.paletteFromBitmap(resource)?.firstOrNull()
                val coverColor = if (color == null) BASE else
                    Color.rgb((color[0] * 255).toInt(), (color[1] * 255).toInt(), (color[2] * 255).toInt())
                paint(view, ColorUtils.blendARGB(BASE, coverColor, .24f))
            }

            override fun onLoadFailed(errorDrawable: Drawable?) = paint(view, BASE)
            override fun onResourceCleared(placeholder: Drawable?) = Unit
        })
    }

    private fun paint(view: View, top: Int) {
        view.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, BOTTOM))
    }
}
