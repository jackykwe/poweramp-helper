package com.kaeonx.poweramphelper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MusicFolderDAO {
    @Query("SELECT * FROM musicfolder ORDER BY `displayName`;")
    fun getAllFlow(): Flow<List<MusicFolder>>

    @Query("UPDATE musicfolder SET `doneMillis` = :doneMillis, `resetMillis` = null WHERE encodedUri = :encodedUri;")
    suspend fun userTick(encodedUri: String, doneMillis: Long)

    @Query("UPDATE musicfolder SET `doneMillis` = null, `resetMillis` = null WHERE encodedUri = :encodedUri;")
    suspend fun userUntick(encodedUri: String)

    @Query("UPDATE musicfolder SET `resetMillis` = :resetMillis WHERE encodedUri IN(:encodedUris);")
    suspend fun automaticUntick(encodedUris: List<String>, resetMillis: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensurePresentFolders(presentMusicFolders: List<MusicFolder>)

    // Returns number of rows successfully deleted
    @Query("DELETE FROM musicfolder WHERE encodedUri NOT IN(:keepEncodedUris);")
    suspend fun deleteObsoleteFolders(keepEncodedUris: List<String>): Int
}