package com.kaeonx.poweramphelper.database

import com.kaeonx.poweramphelper.ui.screens.language.LanguageScreenSortOption
import com.kaeonx.poweramphelper.ui.screens.rating.RatingScreenSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * Reminder: use distinctUntilChanged()
 * Observable queries in Room have one important limitation: the query reruns whenever any row
 * in the table is updated, whether or not that row is in the result set. You can ensure that
 * the UI is only notified when the actual query results change by applying the
 * distinctUntilChanged() operator at the observation site.
 * <https://developer.android.com/training/data-storage/room/async-queries#observable>
 */
internal class MusicFolderRepository(private val musicFolderDAO: MusicFolderDAO) {
    internal fun getAllFlow(): Flow<List<MusicFolder>> {
        return musicFolderDAO.getAllFlow().distinctUntilChanged()
    }

    internal fun getAllWithLangStatsAscendingFlow(
        sortOption: LanguageScreenSortOption,
        pendingFirstSort: Boolean
    ): Flow<List<MusicFolderWithLangStatsDB>> {
        return musicFolderDAO.getAllWithLangStatsAscendingFlow(sortOption.display, pendingFirstSort)
            .distinctUntilChanged()
    }

    internal fun getAllWithLangStatsDescendingFlow(
        sortOption: LanguageScreenSortOption,
        pendingFirstSort: Boolean
    ): Flow<List<MusicFolderWithLangStatsDB>> {
        return musicFolderDAO.getAllWithLangStatsDescendingFlow(sortOption.display, pendingFirstSort)
            .distinctUntilChanged()
    }

    internal fun getAllWithRatingStatsAscendingFlow(
        sortOption: RatingScreenSortOption
    ): Flow<List<MusicFolderWithRatingStatsDB>> {
        return musicFolderDAO.getAllWithRatingStatsAscendingFlow(sortOption.display)
            .distinctUntilChanged()
    }

    internal fun getAllWithRatingStatsDescendingFlow(
        sortOption: RatingScreenSortOption
    ): Flow<List<MusicFolderWithRatingStatsDB>> {
        return musicFolderDAO.getAllWithRatingStatsDescendingFlow(sortOption.display)
            .distinctUntilChanged()
    }

    internal suspend fun userTick(encodedUri: String, doneMillis: Long) {
        musicFolderDAO.userTick(encodedUri, doneMillis)
    }

    internal suspend fun userUntick(encodedUri: String) {
        musicFolderDAO.userUntick(encodedUri)
    }

    internal suspend fun automaticUntick(encodedUris: List<String>, resetMillis: Long) {
        musicFolderDAO.automaticUntick(encodedUris, resetMillis)
    }

    internal suspend fun ensureFoldersSane(presentEncodedUriDisplayNamePairs: List<Pair<String, String>>) {
        musicFolderDAO.deleteObsoleteFolders(presentEncodedUriDisplayNamePairs.map { it.first })
        musicFolderDAO.ensurePresentFolders(
            presentEncodedUriDisplayNamePairs.map {
                MusicFolder(
                    it.first,
                    it.second,
                    null,
                    null
                )
            }
        )
    }
}