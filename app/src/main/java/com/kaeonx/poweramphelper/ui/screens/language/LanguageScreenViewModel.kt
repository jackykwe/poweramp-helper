package com.kaeonx.poweramphelper.ui.screens.language

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.MusicFolder
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.database.MusicFolderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class LanguageScreenViewModel(application: Application) : AndroidViewModel(application) {

    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
//    private val keyStringValuePairRepository = KeyStringValuePairRepository(
//        appDatabaseInstance.keyStringValuePairDao()
//    )

//    private val urisFlow =
//        keyStringValuePairRepository.getFlow(listOf(M3U8_DIR_URI_KSVP_KEY, MUSIC_DIR_URI_KSVP_KEY))

    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())
    private val musicFoldersFlow = musicFolderRepository.getAllFlow()

    internal val languageScreenState = musicFoldersFlow.map { musicFolders ->
        LanguageScreenState(
            musicFolders = musicFolders
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LanguageScreenState(
            musicFolders = listOf()
        )
    )
    // This pattern is better when there are multiple components listening in that should see
    // the same values. The combine code (hot flow) only runs once for all observers. If each
    // observer collected from a cold flow, the combine code runs once for each observer.
    // Courtesy of https://stackoverflow.com/a/66889741


    internal fun userToggle(musicFolder: MusicFolder) {
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
}