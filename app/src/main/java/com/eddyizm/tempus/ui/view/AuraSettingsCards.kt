package com.eddyizm.tempus.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.eddyizm.tempus.R
import com.google.android.material.color.MaterialColors

class AuraSettingsCards(context: Context, private val open: (String) -> Unit) : Preference(context) {
    private var expanded = emptySet<String>()

    init {
        key = "aura_settings_cards"
        order = -100
        layoutResource = R.layout.aura_settings_cards
        isSelectable = false
        isPersistent = false
    }

    fun hasTile(key: String?) = tiles.any { it.key == key }

    fun updateSelection(categories: Set<String>) {
        expanded = categories.toSet()
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val top = holder.findViewById(R.id.aura_settings_cards_top) as LinearLayout
        val bottom = holder.findViewById(R.id.aura_settings_cards_bottom) as LinearLayout
        top.removeAllViews()
        bottom.removeAllViews()
        tiles.forEachIndexed { index, tile ->
            val row = if (index < 2) top else bottom
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (index % 2 == 1) params.marginStart = dp(10)
            row.addView(buildTile(tile), params)
        }
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
    }

    private fun buildTile(tile: Tile): LinearLayout {
        val accent = AuraAppearance.color(context, AuraAppearance.TILES)
        val selected = tile.key in expanded
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(112)
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = cardBackground(context, selected)
            isFocusable = true
            isSelected = selected
            ViewCompat.setScreenReaderFocusable(this, true)
            setOnClickListener { open(tile.key) }
            addView(ImageView(context).apply {
                setImageResource(tile.icon)
                imageTintList = ColorStateList.valueOf(accent)
                background = AuraAppearance.panel(context, 9f, .24f)
                setPadding(dp(7), dp(7), dp(7), dp(7))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(TextView(context).apply {
                setText(tile.title)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(TextView(context).apply {
                setText(tile.summary)
                textSize = 12f
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    private data class Tile(val key: String, val title: Int, val summary: Int, val icon: Int)

    companion object {
        private val tiles = listOf(
            Tile("aura_appearance", R.string.aura_appearance_title, R.string.aura_settings_card_appearance, R.drawable.ic_settings),
            Tile("settings_sound", R.string.settings_title_sound, R.string.aura_settings_card_sound, R.drawable.ic_albums),
            Tile("settings_storage", R.string.aura_settings_downloads, R.string.aura_settings_card_downloads, R.drawable.ic_download),
            Tile("settings_interface", R.string.aura_settings_home, R.string.aura_settings_card_home, R.drawable.ic_home),
        )

        @JvmStatic
        fun cardBackground(context: Context, selected: Boolean): RippleDrawable {
            val accent = AuraAppearance.color(context, AuraAppearance.TILES)
            val fill = AuraAppearance.panel(context, 18f, if (selected) .24f else .13f)
            val density = context.resources.displayMetrics.density
            fill.setStroke(density.toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(accent, if (selected) 145 else 45))
            return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 54)), fill, null)
        }
    }
}
