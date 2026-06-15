package com.rembuk.rembuktv.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_sources")
data class PlaylistSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val type: String, // PlaylistType name
    val enabled: Boolean = true,
    val lastSyncedAt: Long? = null,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("group_title"), Index("name")],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: Long,
    val name: String,
    val logoUrl: String?,
    @ColumnInfo(name = "group_title") val group: String?,
    val streamUrl: String,
    val streamType: String,
    val drmScheme: String?,
    val drmLicenseUrl: String?,
    /** Static ClearKey pairs serialized as "kid:key,kid:key" (no license server). */
    val drmClearKeys: String? = null,
    val tvgId: String?,
    /** Preserves the original playlist ordering. */
    val sortIndex: Int,
    /** Custom HTTP headers stored as a semi-colon separated string or JSON.
     * Format: "Key1:Value1|Key2:Value2" */
    val headers: String? = null,
    val isFree: Boolean = false,
    val locked: Boolean = false,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val channelId: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val channelId: String,
    val watchedAt: Long,
)

/** Projection: a playlist source plus its cached channel count. */
data class PlaylistSourceWithCount(
    val id: Long,
    val name: String,
    val url: String,
    val type: String,
    val enabled: Boolean,
    val lastSyncedAt: Long?,
    val channelCount: Int,
)
