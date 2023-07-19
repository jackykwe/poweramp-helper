package com.kaeonx.poweramphelper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// KSVP is KeyStringValuePair
internal const val M3U8_DIR_URI_KSVP_KEY = "m3u8DirUri"
internal const val MUSIC_DIR_URI_KSVP_KEY = "musicDirUri"
internal const val LAST_ANALYSIS_MILLIS_KSVP_KEY = "lastAnalysisTs"

@Entity
internal data class KeyStringValuePair(
    @PrimaryKey val key: String,
    val value: String
)