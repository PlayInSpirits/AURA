package com.eddyizm.tempus.subsonic.models

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
@Entity(tableName = "playlist")
open class Playlist(
    @PrimaryKey
    @ColumnInfo(name = "id")
    open var id: String,
    @ColumnInfo(name = "name")
    var name: String? = null,
    @ColumnInfo(name = "duration")
    var duration: Long = 0,
    @SerializedName("coverArt")
    @ColumnInfo(name = "coverArt")
    var coverArtId: String? = null,
) : Parcelable {
    var comment: String? = null
    var owner: String? = null
    @SerializedName("public")
    var isUniversal: Boolean? = null
    @ColumnInfo(name = "songCount", defaultValue = "0")
    var songCount: Int = 0
    @ColumnInfo(name = "created")
    var created: Date? = null
    @ColumnInfo(name = "changed")
    var changed: Date? = null
    @ColumnInfo(name = "lastPlayed", defaultValue = "0")
    var lastPlayed: Long = 0
    @ColumnInfo(name = "allowedUsers")
    var allowedUsers: List<String>? = null
    @Ignore
    constructor(
        id: String,
        name: String?,
        comment: String?,
        owner: String?,
        isUniversal: Boolean?,
        songCount: Int,
        duration: Long,
        created: Date?,
        changed: Date?,
        coverArtId: String?,
        allowedUsers: List<String>?,
    ) : this(id, name, duration, coverArtId) {
        this.comment = comment
        this.owner = owner
        this.isUniversal = isUniversal
        this.songCount = songCount
        this.created = created
        this.changed = changed
        this.lastPlayed = 0
        this.allowedUsers = allowedUsers
    }

    /**
     * Parcelize only writes the primary-constructor properties, so without this the body properties
     * (songCount, comment, owner, public flag, dates, allowed users) were lost whenever a Playlist went
     * through a Bundle that got parcelled (fragment arguments after the app was restored): the page
     * then showed 0 songs and the editor could reset the playlist's public flag.
     */
    companion object : Parceler<Playlist> {
        override fun Playlist.write(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeString(name)
            parcel.writeLong(duration)
            parcel.writeString(coverArtId)
            parcel.writeString(comment)
            parcel.writeString(owner)
            parcel.writeValue(isUniversal)
            parcel.writeInt(songCount)
            parcel.writeLong(created?.time ?: NO_DATE)
            parcel.writeLong(changed?.time ?: NO_DATE)
            parcel.writeLong(lastPlayed)
            parcel.writeStringList(allowedUsers)
        }

        override fun create(parcel: Parcel): Playlist {
            val playlist = Playlist(
                id = parcel.readString().orEmpty(),
                name = parcel.readString(),
                duration = parcel.readLong(),
                coverArtId = parcel.readString(),
            )
            playlist.comment = parcel.readString()
            playlist.owner = parcel.readString()
            playlist.isUniversal = parcel.readValue(Boolean::class.java.classLoader) as Boolean?
            playlist.songCount = parcel.readInt()
            playlist.created = parcel.readLong().takeIf { it != NO_DATE }?.let { Date(it) }
            playlist.changed = parcel.readLong().takeIf { it != NO_DATE }?.let { Date(it) }
            playlist.lastPlayed = parcel.readLong()
            playlist.allowedUsers = parcel.createStringArrayList()
            return playlist
        }

        private const val NO_DATE = Long.MIN_VALUE
    }
}