package com.eddyizm.tempus.ui.view

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import com.bumptech.glide.Glide
import com.eddyizm.tempus.R
import com.eddyizm.tempus.ui.activity.MainActivity

@UnstableApi
class AuraMiniPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private var browser: MediaBrowser? = null
    private var art: Uri? = null
    var onItemVisibility: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(browser?.currentMediaItem != null)
        }
    private val cover = ImageView(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.aura_wave_cover_surface)
        clipToOutline = true
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val title = line(14f, ContextCompat.getColor(context, R.color.aura_player_foreground))
    private val play = control(R.drawable.aura_play_rounded, R.string.widget_content_desc_play_pause) {
        browser?.let { if (it.playWhenReady) it.pause() else it.play() }
    }
    private val next = control(R.drawable.ic_skip_next, R.string.widget_content_desc_next) {
        browser?.seekToNextMediaItem()
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = render()
    }

    init {
        gravity = Gravity.CENTER_VERTICAL
        orientation = HORIZONTAL
        setPadding(dp(8), 0, dp(6), 0)
        setBackgroundResource(R.drawable.aura_mini_player_surface)
        addView(cover, LayoutParams(dp(40), dp(40)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
            addView(title)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(play, LayoutParams(dp(46), dp(48)))
        addView(next, LayoutParams(dp(40), dp(48)))
        setOnClickListener { (context as? MainActivity)?.expandBottomSheet() }
        setOnTouchListener(AuraTrackGestures(
            { browser?.currentMediaItem != null },
            { browser?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } },
            { browser?.pause() },
        ))
        visibility = INVISIBLE
    }

    fun bind(player: MediaBrowser?) {
        if (browser !== player) {
            browser?.removeListener(listener)
            browser = player
            player?.addListener(listener)
        }
        render()
    }

    override fun onDetachedFromWindow() {
        browser?.removeListener(listener)
        browser = null
        Glide.with(cover).clear(cover)
        art = null
        super.onDetachedFromWindow()
    }

    private fun render() {
        val player = browser
        val metadata = player?.mediaMetadata
        val hasItem = player?.currentMediaItem != null
        val target = if (hasItem) VISIBLE else INVISIBLE
        if (visibility != target) {
            animate().cancel()
            visibility = target
            onItemVisibility?.invoke(hasItem)
            if (hasItem) {
                alpha = 0f
                animate().alpha(1f).setDuration(155).start()
            }
        }
        title.text = metadata?.title ?: context.getString(R.string.aura_now_playing)
        val coverUri = metadata?.artworkUri
        if (art != coverUri || cover.drawable == null) {
            art = coverUri
            Glide.with(cover).load(art).placeholder(R.drawable.ic_albums).error(R.drawable.ic_albums).into(cover)
        }
        val playing = player?.playWhenReady == true
        play.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.aura_play_rounded)
        play.contentDescription = context.getString(if (playing) R.string.player_queue_pause else R.string.player_queue_play)
        next.isEnabled = player?.hasNextMediaItem() == true
        next.alpha = if (next.isEnabled) 1f else 0.4f
    }

    private fun dp(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun line(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun control(icon: Int, label: Int, action: () -> Unit) = ImageButton(context).apply {
        setBackgroundResource(android.R.color.transparent)
        setImageResource(icon)
        contentDescription = context.getString(label)
        setColorFilter(ContextCompat.getColor(context, R.color.aura_player_accent))
        setOnClickListener { action() }
    }
}
