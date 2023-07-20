package com.kaeonx.poweramphelper.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["parentDirEncodedUri", "fileName"],
    foreignKeys = [
        ForeignKey(
            entity = MusicFolder::class,
            parentColumns = ["encodedUri"],
            childColumns = ["parentDirEncodedUri"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
            deferred = false,
        )
    ]
)
internal data class MusicFile(
    internal val parentDirEncodedUri: String,
    internal val fileName: String,
    internal val rating: Int,
    internal val langEN: Boolean = false,    // ENG
    internal val langCN: Boolean = false,    // CHN
    internal val langJP: Boolean = false,    // JAP
    internal val langKR: Boolean = false,    // KOR
    internal val langO: Boolean = false,     // Others
    internal val langCh: Boolean = false    // Choral
)

