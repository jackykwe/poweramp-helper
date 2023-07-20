package com.kaeonx.poweramphelper.ui.screens.language

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.KeyStringValuePairRepository
import com.kaeonx.poweramphelper.database.LANG_SCR_DESC_SORT_KSVP_KEY
import com.kaeonx.poweramphelper.database.LANG_SCR_PEND_FIRST_SORT_KSVP_KEY
import com.kaeonx.poweramphelper.database.LANG_SCR_SORT_OPTION_KSVP_KEY
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.database.MusicFolderState
import com.kaeonx.poweramphelper.database.MusicFolderWithLangStats
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
        val musicFoldersWithStatistics = if (descendingSort) {
            musicFolderRepository.getAllWithLangStatsDescendingFlow(sortOption, pendingFirstSort)
        } else {
            musicFolderRepository.getAllWithLangStatsAscendingFlow(sortOption, pendingFirstSort)
        }
        musicFoldersWithStatistics.map {
            LanguageScreenState(
                musicFoldersWithStatistics = it,
                sortOption = sortOption,
                pendingFirstSort = pendingFirstSort,
                descendingSort = descendingSort
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LanguageScreenState(
            musicFoldersWithStatistics = listOf(),
            sortOption = LanguageScreenSortOption.NAME,
            pendingFirstSort = false,
            descendingSort = false
        )
    )
    // This pattern is better when there are multiple components listening in that should see
    // the same values. The combine code (hot flow) only runs once for all observers. If each
    // observer collected from a cold flow, the combine code runs once for each observer.
    // Courtesy of https://stackoverflow.com/a/66889741


    internal fun userToggle(musicFolder: MusicFolderWithLangStats) {
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
}