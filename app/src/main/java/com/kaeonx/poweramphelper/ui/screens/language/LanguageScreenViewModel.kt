package com.kaeonx.poweramphelper.ui.screens.language

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
import com.kaeonx.poweramphelper.database.LANG_SCR_DESC_SORT_KSVP_KEY
import com.kaeonx.poweramphelper.database.LANG_SCR_PEND_FIRST_SORT_KSVP_KEY
import com.kaeonx.poweramphelper.database.LANG_SCR_SORT_OPTION_KSVP_KEY
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.database.MusicFolderState
import com.kaeonx.poweramphelper.database.MusicFolderWithLangStatsDB
import com.kaeonx.poweramphelper.database.MusicFolderWithLangStatsUI
import com.kaeonx.poweramphelper.utils.millisToDisplayWithoutTZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class LanguageScreenViewModel(application: Application) : AndroidViewModel(application) {

    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
    private val keyStringValuePairRepository = KeyStringValuePairRepository(
        appDatabaseInstance.keyStringValuePairDao()
    )

    private val sortOptionsFlow = keyStringValuePairRepository.getFlow(
        listOf(
            LANG_SCR_SORT_OPTION_KSVP_KEY,
            LANG_SCR_PEND_FIRST_SORT_KSVP_KEY,
            LANG_SCR_DESC_SORT_KSVP_KEY
        )
    )
    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())

    // flatMapConcat saves the day again;
    // inspiration from https://betterprogramming.pub/sorting-and-filtering-records-using-room-database-and-kotlin-flow-c64ccdb39deb
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val languageScreenState = sortOptionsFlow.flatMapLatest { sortOptions ->
        val sortOption = LanguageScreenSortOption.fromString(
            sortOptions[LANG_SCR_SORT_OPTION_KSVP_KEY]
                ?: LanguageScreenSortOption.NAME.display
        )
        val pendingFirstSort = sortOptions[LANG_SCR_PEND_FIRST_SORT_KSVP_KEY].toBoolean()
        val descendingSort = sortOptions[LANG_SCR_DESC_SORT_KSVP_KEY].toBoolean()
        val musicFoldersWithLangStatsFlow = if (descendingSort) {
            musicFolderRepository.getAllWithLangStatsDescendingFlow(sortOption, pendingFirstSort)
        } else {
            musicFolderRepository.getAllWithLangStatsAscendingFlow(sortOption, pendingFirstSort)
        }
        musicFoldersWithLangStatsFlow.map { musicFoldersWithLangStats ->
            LanguageScreenState(
                musicFoldersWithLangStats = musicFoldersWithLangStats.map {
                    val state = when {
                        it.doneMillis == null -> MusicFolderState.NOT_DONE
                        it.resetMillis == null -> MusicFolderState.DONE
                        else -> MusicFolderState.DONE_AUTO_RESET
                    }
                    MusicFolderWithLangStatsUI(
                        encodedUri = it.encodedUri,
                        dirName = it.dirName,
                        state = state,
                        countReport = generateCountReportAS(it),
                        timestampsReport = generateTimestampsReportAS(it, state)
                    )
                },
                sortOption = sortOption,
                pendingFirstSort = pendingFirstSort,
                descendingSort = descendingSort,
                sortString = generateSortString(
                    sortOption = sortOption,
                    pendingFirstSort = pendingFirstSort,
                    descendingSort = descendingSort,
                ),
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LanguageScreenState(
            musicFoldersWithLangStats = listOf(),
            sortOption = LanguageScreenSortOption.NAME,
            pendingFirstSort = false,
            descendingSort = false,
            sortString = ""
        )
    )
    // This pattern is better when there are multiple components listening in that should see
    // the same values. The combine code (hot flow) only runs once for all observers. If each
    // observer collected from a cold flow, the combine code runs once for each observer.
    // Courtesy of https://stackoverflow.com/a/66889741


    internal fun userToggle(musicFolder: MusicFolderWithLangStatsUI) {
        /*
            NOT DONE     -[User tick]->                                   DONE
            NOT DONE     <-[User untick]-                                 DONE
            DONE (RESET) -[User tick]->                                   DONE
            DONE (RESET) <-[automatic detect change millis > doneMillis]- DONE
         */
        viewModelScope.launch(Dispatchers.IO) {
            when (musicFolder.state) {
                MusicFolderState.NOT_DONE -> {
                    musicFolderRepository.userTick(
                        encodedUri = musicFolder.encodedUri,
                        doneMillis = System.currentTimeMillis()
                    )
                }

                MusicFolderState.DONE -> {
                    musicFolderRepository.userUntick(musicFolder.encodedUri)
                }

                MusicFolderState.DONE_AUTO_RESET -> {
                    musicFolderRepository.userTick(
                        encodedUri = musicFolder.encodedUri,
                        doneMillis = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    internal fun saveSortOptions(
        sortOption: LanguageScreenSortOption,
        pendingFirstSort: Boolean,
        descendingSort: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            keyStringValuePairRepository.put(
                listOf(
                    LANG_SCR_SORT_OPTION_KSVP_KEY to sortOption.display,
                    LANG_SCR_PEND_FIRST_SORT_KSVP_KEY to pendingFirstSort.toString(),
                    LANG_SCR_DESC_SORT_KSVP_KEY to descendingSort.toString()
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
    private val redStyle = SpanStyle(color = Color.Red)

    private fun generateCountReportAS(stats: MusicFolderWithLangStatsDB): AnnotatedString {
        return buildAnnotatedString {
            append(stats.langENSum.toString())
            withStyle(superscriptStyle) {
                append("EN  ")
            }
            append(stats.langCNSum.toString())
            withStyle(superscriptStyle) {
                append("CN  ")
            }
            append(stats.langJPSum.toString())
            withStyle(superscriptStyle) {
                append("JP  ")
            }
            append(stats.langKRSum.toString())
            withStyle(superscriptStyle) {
                append("KR  ")
            }
            append(stats.langOSum.toString())
            withStyle(superscriptStyle) {
                append("O  ")
            }
            append(stats.langChSum.toString())
            withStyle(superscriptStyle) {
                append("Ch")
            }
            append("\n")
            append((stats.fileCount - stats.minusSum).toString())
            withStyle(subscriptStyle) {
                append("Song  ")
            }
            append((stats.minusSum).toString())
            withStyle(subscriptStyle) {
                append("-  ")
            }
            append(stats.fileCount.toString())
            withStyle(subscriptStyle) {
                append("Σ  ")
            }
        }
    }

    private fun generateTimestampsReportAS(
        stats: MusicFolderWithLangStatsDB,
        state: MusicFolderState
    ): AnnotatedString {
        return buildAnnotatedString {
            when (state) {
                MusicFolderState.NOT_DONE -> {}
                MusicFolderState.DONE -> {
                    withStyle(greenStyle) {
                        append("(done) ")
                        append(millisToDisplayWithoutTZ(stats.doneMillis!!))
                    }
                }

                MusicFolderState.DONE_AUTO_RESET -> {
                    withStyle(redStyle) {
                        append("(done) ")
                        append(millisToDisplayWithoutTZ(stats.doneMillis!!))
                        append("\n(reset) ")
                        append(millisToDisplayWithoutTZ(stats.resetMillis!!))
                    }
                }
            }
        }
    }

    private fun generateSortString(
        sortOption: LanguageScreenSortOption,
        pendingFirstSort: Boolean,
        descendingSort: Boolean
    ): String {
        return buildString {
            append("Sort: ")
            append(sortOption.display)
            if (pendingFirstSort) append(" ⋯")
            if (descendingSort) append(" ▼") else append(" ▲")
        }
    }
}