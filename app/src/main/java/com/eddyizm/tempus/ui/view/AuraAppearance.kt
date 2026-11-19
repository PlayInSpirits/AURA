package com.eddyizm.tempus.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors

object AuraAppearance {
    const val LOGO = "aura_color_logo"
    const val SETTINGS = "aura_color_settings"
    const val TILES = "aura_color_tiles"
    const val GLOW = "aura_text_glow"
    const val BLUR = "aura_soft_wave"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    @JvmStatic
    fun color(context: Context, key: String): Int {
        val fallback = when (key) {
            SETTINGS -> MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
            TILES -> if (dark(context)) 0xFFC9B2FF.toInt() else 0xFF724BAE.toInt()
            else -> if (dark(context)) 0xFFE2D2FF.toInt() else 0xFF835CB4.toInt()
        }
        return prefs(context).getInt(key, fallback)
    }

    private fun dark(context: Context): Boolean =
        !MaterialColors.isColorLight(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK))

    @JvmStatic
    fun softness(context: Context): Float = prefs(context).getInt("aura_wave_softness", 60).coerceIn(0, 100) / 100f

    @JvmStatic
    fun softWave(context: Context): Boolean = prefs(context).getBoolean(BLUR, true)

    @JvmStatic
    fun applyWordmark(text: TextView) {
        val color = color(text.context, LOGO)
        val glow = if (prefs(text.context).getBoolean(GLOW, true)) ColorUtils.setAlphaComponent(color, 64) else Color.TRANSPARENT
        text.setTextColor(color)
        text.setShadowLayer(4f * text.resources.displayMetrics.density, 0f, 0f, glow)
    }

    @JvmStatic
    fun navigationTint(context: Context): ColorStateList {
        val muted = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY)
        return ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(color(context, TILES), muted))
    }

    @JvmStatic
    fun panel(context: Context, radius: Float, strength: Float): GradientDrawable {
        val base = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        val accent = color(context, TILES)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            ColorUtils.blendARGB(base, accent, strength),
            ColorUtils.blendARGB(base, accent, strength / 3)
        )).apply { cornerRadius = radius * context.resources.displayMetrics.density }
    }
}
