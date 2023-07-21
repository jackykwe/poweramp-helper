package com.kaeonx.poweramphelper.ui.screens.languageFolder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.MusicFileRepository
import com.kaeonx.poweramphelper.database.MusicFileUI
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.utils.mapFromIntLintString
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class LanguageFolderScreenViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val folderEncodedUri = mapFromIntLintString(
        checkNotNull(savedStateHandle["folderEncodedUri"]) as String
    )

    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())

    private val musicFileRepository = MusicFileRepository(appDatabaseInstance.musicFileDao())
    private val musicFilesLangMinusFlow =
        musicFileRepository.getMusicFilesInMusicFolderLangMinusFlow(folderEncodedUri)

    internal val languageFolderScreenState = musicFilesLangMinusFlow.map { musicFilesLangMinus ->
        val dirName = musicFolderRepository.getDirNameFromEncodedUri(folderEncodedUri)
        LanguageFolderScreenState(
            dirName = dirName,
            musicFiles = musicFilesLangMinus.map {
                MusicFileUI(
                    parentDirEncodedUri = it.parentDirEncodedUri,
                    fileName = it.fileName
                )
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LanguageFolderScreenState(
            dirName = "",
            musicFiles = listOf()
        )
    )
}