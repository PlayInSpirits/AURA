package com.eddyizm.tempus.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.google.android.material.bottomnavigation.BottomNavigationView

class AuraBottomNavigation @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BottomNavigationView(context, attrs) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val items = getChildAt(0) as? ViewGroup ?: return
        if (items.childCount != 2) return
        val itemWidth = (64 * resources.displayMetrics.density).toInt()
        val height = items.height
        items.layout(paddingLeft, items.top, width - paddingRight, items.top + height)
        for (index in 0..1) {
            val item = items.getChildAt(index)
            val start = if ((index == 0) == (layoutDirection != LAYOUT_DIRECTION_RTL)) 0 else items.width - itemWidth
            item.layout(start, 0, start + itemWidth, height)
        }
    }
}
