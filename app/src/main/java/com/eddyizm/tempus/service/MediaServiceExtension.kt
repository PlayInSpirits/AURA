package com.eddyizm.tempus.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

interface MediaServiceExtension {
    fun handle(
        player: Player,
        item: MediaItem,
        queueTarget: MediaManager.QueueTarget
    ): Boolean

    fun onPlaybackChanged(player: Player, queueTarget: MediaManager.QueueTarget) {}

    fun onTrackEnded(item: MediaItem, positionMs: Long, completed: Boolean) {}
}

object MediaServiceExtensionRegistry {
    var handler: MediaServiceExtension? = null
}
