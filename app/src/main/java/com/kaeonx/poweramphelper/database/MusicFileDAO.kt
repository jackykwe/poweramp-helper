package com.kaeonx.poweramphelper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MusicFileDAO {
    @Query("SELECT * FROM musicfile WHERE `parentDirEncodedUri` = :parentDirEncodedUri ORDER BY `fileName`;")
    fun getMusicFilesInMusicFolderFlow(parentDirEncodedUri: String): Flow<List<MusicFile>>

    @Query("UPDATE musicfile SET `langCh` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangCh(parentDirEncodedUri: String, fileName: String)

    @Query("UPDATE musicfile SET `langCN` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangCN(parentDirEncodedUri: String, fileName: String)

    @Query("UPDATE musicfile SET `langEN` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangEN(parentDirEncodedUri: String, fileName: String)

    @Query("UPDATE musicfile SET `langJP` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangJP(parentDirEncodedUri: String, fileName: String)

    @Query("UPDATE musicfile SET `langKR` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangKR(parentDirEncodedUri: String, fileName: String)

    @Query("UPDATE musicfile SET `langO` = 1 WHERE `parentDirEncodedUri` = :parentDirEncodedUri AND `fileName` = :fileName;")
    suspend fun setLangO(parentDirEncodedUri: String, fileName: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensurePresentFiles(musicFiles: List<MusicFile>)

    // Returns number of rows succfyessfully deleted
    @Query("DELETE FROM musicfile;")
    suspend fun reset(): Int
}