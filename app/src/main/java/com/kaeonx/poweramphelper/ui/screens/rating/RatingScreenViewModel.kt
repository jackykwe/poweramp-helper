package com.kaeonx.poweramphelper.ui.screens.rating

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.KeyStringValuePairRepository
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.database.MusicFolderWithRatingStatsDB
import com.kaeonx.poweramphelper.database.MusicFolderWithRatingStatsUI
import com.kaeonx.poweramphelper.database.RATING_SCR_DESC_SORT_KSVP_KEY
import com.kaeonx.poweramphelper.database.RATING_SCR_SORT_OPTION_KSVP_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class RatingScreenViewModel(application: Application) : AndroidViewModel(application) {

    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
    private val keyStringValuePairRepository = KeyStringValuePairRepository(
        appDatabaseInstance.keyStringValuePairDao()
    )

    private val sortOptionsFlow = keyStringValuePairRepository.getFlow(
        listOf(
            RATING_SCR_SORT_OPTION_KSVP_KEY,
            RATING_SCR_DESC_SORT_KSVP_KEY
        )
    )
    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())

    // flatMapConcat saves the day again;
    // inspiration from https://betterprogramming.pub/sorting-and-filtering-records-using-room-database-and-kotlin-flow-c64ccdb39deb
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val ratingScreenState = sortOptionsFlow.flatMapLatest { sortOptions ->
        val sortOption = RatingScreenSortOption.fromString(
            sortOptions[RATING_SCR_SORT_OPTION_KSVP_KEY]
                ?: RatingScreenSortOption.NAME.display
        )
        val descendingSort = sortOptions[RATING_SCR_DESC_SORT_KSVP_KEY].toBoolean()
        val musicFoldersWithStatisticsFlow = if (descendingSort) {
            musicFolderRepository.getAllWithRatingStatsDescendingFlow(sortOption)
        } else {
            musicFolderRepository.getAllWithRatingStatsAscendingFlow(sortOption)
        }
        musicFoldersWithStatisticsFlow.map { musicFoldersWithStatistics ->
            RatingScreenState(
                musicFoldersWithRatingStats = musicFoldersWithStatistics.map {
                    val ratingGt0SSum =
                        it.rating1SSum + it.rating2SSum + it.rating3SSum + it.rating4SSum + it.rating5SSum
                    MusicFolderWithRatingStatsUI(
                        encodedUri = it.encodedUri,
                        dirName = it.dirName,
                        countReport = generateCountReportAS(it, ratingGt0SSum),
                        progressReport = generateProgressReportAS(it, ratingGt0SSum)
                    )
                },
                sortOption = sortOption,
                descendingSort = descendingSort,
                sortString = generateSortString(sortOption, descendingSort)
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        RatingScreenState(
            musicFoldersWithRatingStats = listOf(),
            sortOption = RatingScreenSortOption.NAME,
            descendingSort = false,
            sortString = ""
        )
    )
    // This pattern is better when there are multiple components listening in that should see
    // the same values. The combine code (hot flow) only runs once for all observers. If each
    // observer collected from a cold flow, the combine code runs once for each observer.
    // Courtesy of https://stackoverflow.com/a/66889741

    internal fun saveSortOptions(sortOption: RatingScreenSortOption, descendingSort: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            keyStringValuePairRepository.put(
                listOf(
                    RATING_SCR_SORT_OPTION_KSVP_KEY to sortOption.display,
                    RATING_SCR_DESC_SORT_KSVP_KEY to descendingSort.toString()
                )
            )
        }
    }

    ////////////////////////
    // UI State Functions //
    ////////////////////////
    private val superscriptStyle = SpanStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Light,
        baselineShift = BaselineShift.Superscript
    )

    private val subscriptStyle = SpanStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Light,
        baselineShift = BaselineShift.Subscript
    )

    private val greenStyle = SpanStyle(color = Color.Green)
    private val grayStyle = SpanStyle(color = Color.Gray)

    private fun generateCountReportAS(
        stats: MusicFolderWithRatingStatsDB,
        ratingGt0SSum: Int
    ): AnnotatedString {
        return buildAnnotatedString {
            append(stats.rating0SSum.toString())
            withStyle(superscriptStyle) {
                append("零  ")
            }
            append(stats.rating1SSum.toString())
            withStyle(superscriptStyle) {
                append("壱  ")
            }
            append(stats.rating2SSum.toString())
            withStyle(superscriptStyle) {
                append("弐  ")
            }
            append(stats.rating3SSum.toString())
            withStyle(superscriptStyle) {
                append("参  ")
            }
            append(stats.rating4SSum.toString())
            withStyle(superscriptStyle) {
                append("肆  ")
            }
            append(stats.rating5SSum.toString())
            withStyle(superscriptStyle) {
                append("伍")
            }
            append("\n")
            append(ratingGt0SSum.toString())
            withStyle(subscriptStyle) {
                append("Rated  ")
            }
            append(stats.fileCount.toString())
            withStyle(subscriptStyle) {
                append("Σ  ")
            }
        }
    }

    private fun generateProgressReportAS(
        stats: MusicFolderWithRatingStatsDB,
        ratingGt0SSum: Int
    ): AnnotatedString {
        val pct = (100 * ratingGt0SSum).div(stats.fileCount)
        return buildAnnotatedString {
            if (pct == 100) {
                withStyle(greenStyle) {
                    append("100%")
                }
            } else {
                withStyle(grayStyle) {
                    append("$pct%")
                }
            }
        }
    }

    private fun generateSortString(
        sortOption: RatingScreenSortOption,
        descendingSort: Boolean
    ): String {
        return buildString {
            append("Sort: ")
            append(sortOption.display)
            if (descendingSort) append(" ▼") else append(" ▲")
        }
    }
}