package com.kaeonx.poweramphelper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MusicFolderDAO {
    @Query("SELECT * FROM musicfolder;")
    fun getAllFlow(): Flow<List<MusicFolder>>

    // [ ] DONE (RESET) (doneMillis != null, resetMillis != null)  #1
    // [ ] NOT DONE     (doneMillis == null, resetMillis == null)  #2
    // [v] DONE         (doneMillis != null, resetMillis == null)  #3
    @Query(
        // Inspiration courtesy of https://stackoverflow.com/a/62460199
        // Other inspirations for CASE WHEN * THEN * END queries and large queries:
        // - https://stackoverflow.com/a/55297208
        // - https://stackoverflow.com/a/61070980
        // - https://www.sqlitetutorial.net/sqlite-case/
        "SELECT *, CASE WHEN `doneMillis` IS NOT NULL AND `resetMillis` IS NOT NULL THEN 1 " +                        // Case #1 (DONE (RESET))
        "               WHEN `doneMillis` IS NULL AND `resetMillis` IS NULL THEN 2 " +                                // Case #2 (NOT DONE)
        "               WHEN `doneMillis` IS NOT NULL AND `resetMillis` IS NULL THEN 3 END AS `pendingFirstSort` " +  // Case #3 (DONE)
        "FROM musicfolder " +
        "INNER JOIN (SELECT `parentDirEncodedUri`, count(`fileName`) AS fileCount, " +
        "            sum(`langCh`) AS `langChSum`, sum(`langCN`) AS `langCNSum`, sum(`langEN`) AS `langENSum`, " +
        "            sum(`langJP`) AS `langJPSum`, sum(`langKR`) AS `langKRSum`, sum(`langO`) AS `langOSum`, " +
        "            count(`fileName`) - sum(`langCh` = 1 OR `langCN` = 1 OR `langEN` = 1 OR `langJP` = 1 OR `langKR` = 1 OR `langO` = 1) AS `minusCount` " +
        "            FROM musicfile GROUP BY `parentDirEncodedUri`) AS subquery " +
        "ON musicfolder.encodedUri = subquery.parentDirEncodedUri " +
        "ORDER BY CASE WHEN :pendingFirstSort THEN `pendingFirstSort` END ASC, " +
        "         CASE WHEN :sortOption = 'Name' THEN `dirName` " +
        "              WHEN :sortOption = 'EN Count' THEN `langENSum`" +
        "              WHEN :sortOption = 'CN Count' THEN `langCNSum` " +
        "              WHEN :sortOption = 'JP Count' THEN `langJPSum` " +
        "              WHEN :sortOption = 'KR Count' THEN `langKRSum` " +
        "              WHEN :sortOption = 'O Count' THEN `langOSum` " +
        "              WHEN :sortOption = 'Ch Count' THEN `langChSum` " +
        "              WHEN :sortOption = '- Count' THEN `minusCount` " +
        "              WHEN :sortOption = 'Σ Count' THEN `fileCount` END ASC;"
    )
    fun getAllWithLangStatsAscendingFlow(sortOption: String, pendingFirstSort: Boolean):
            Flow<List<MusicFolderWithLangStats>>

    @Query(
        "SELECT *, CASE WHEN `doneMillis` IS NOT NULL AND `resetMillis` IS NOT NULL THEN 1 " +                        // Case #1 (DONE (RESET))
        "               WHEN `doneMillis` IS NULL AND `resetMillis` IS NULL THEN 2 " +                                // Case #2 (NOT DONE)
        "               WHEN `doneMillis` IS NOT NULL AND `resetMillis` IS NULL THEN 3 END AS `pendingFirstSort` " +  // Case #3 (DONE)
        "FROM musicfolder " +
        "INNER JOIN (SELECT `parentDirEncodedUri`, count(`fileName`) AS fileCount, " +
        "            sum(`langCh`) AS `langChSum`, sum(`langCN`) AS `langCNSum`, sum(`langEN`) AS `langENSum`, " +
        "            sum(`langJP`) AS `langJPSum`, sum(`langKR`) AS `langKRSum`, sum(`langO`) AS `langOSum`, " +
        "            count(`fileName`) - sum(`langCh` = 1 OR `langCN` = 1 OR `langEN` = 1 OR `langJP` = 1 OR `langKR` = 1 OR `langO` = 1) AS `minusCount` " +
        "            FROM musicfile GROUP BY `parentDirEncodedUri`) AS subquery " +
        "ON musicfolder.encodedUri = subquery.parentDirEncodedUri " +
        "ORDER BY CASE WHEN :pendingFirstSort THEN `pendingFirstSort` END ASC, " +
        "         CASE WHEN :sortOption = 'Name' THEN `dirName` " +
        "              WHEN :sortOption = 'EN Count' THEN `langENSum`" +
        "              WHEN :sortOption = 'CN Count' THEN `langCNSum` " +
        "              WHEN :sortOption = 'JP Count' THEN `langJPSum` " +
        "              WHEN :sortOption = 'KR Count' THEN `langKRSum` " +
        "              WHEN :sortOption = 'O Count' THEN `langOSum` " +
        "              WHEN :sortOption = 'Ch Count' THEN `langChSum` " +
        "              WHEN :sortOption = '- Count' THEN `minusCount` " +
        "              WHEN :sortOption = 'Σ Count' THEN `fileCount` END DESC;"
    )
    fun getAllWithLangStatsDescendingFlow(sortOption: String, pendingFirstSort: Boolean):
            Flow<List<MusicFolderWithLangStats>>

    @Query("UPDATE musicfolder SET `doneMillis` = :doneMillis, `resetMillis` = null WHERE `encodedUri` = :encodedUri;")
    suspend fun userTick(encodedUri: String, doneMillis: Long)

    @Query("UPDATE musicfolder SET `doneMillis` = null, `resetMillis` = null WHERE `encodedUri` = :encodedUri;")
    suspend fun userUntick(encodedUri: String)

    @Query("UPDATE musicfolder SET `resetMillis` = :resetMillis WHERE `encodedUri` IN(:encodedUris);")
    suspend fun automaticUntick(encodedUris: List<String>, resetMillis: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensurePresentFolders(presentMusicFolders: List<MusicFolder>)

    // Returns number of rows successfully deleted
    @Query("DELETE FROM musicfolder WHERE `encodedUri` NOT IN(:keepEncodedUris);")
    suspend fun deleteObsoleteFolders(keepEncodedUris: List<String>): Int
}