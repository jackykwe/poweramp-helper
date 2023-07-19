package com.kaeonx.poweramphelper.ui.screens.home

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.KeyStringValuePairRepository
import com.kaeonx.poweramphelper.database.LAST_ANALYSIS_MILLIS_KSVP_KEY
import com.kaeonx.poweramphelper.database.M3U8_DIR_URI_KSVP_KEY
import com.kaeonx.poweramphelper.database.MUSIC_DIR_URI_KSVP_KEY
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "homeScreenViewModel"

internal class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {
    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
    private val keyStringValuePairRepository = KeyStringValuePairRepository(
        appDatabaseInstance.keyStringValuePairDao()
    )

    private val urisFlow =
        keyStringValuePairRepository.getFlow(listOf(M3U8_DIR_URI_KSVP_KEY, MUSIC_DIR_URI_KSVP_KEY))
    private val lastAnalysisMillisFlow =
        keyStringValuePairRepository.getFlow(LAST_ANALYSIS_MILLIS_KSVP_KEY).map { it?.toLong() }

    internal val homeScreenState =
        combine(urisFlow, lastAnalysisMillisFlow) { uris, lastAnalysisMillis ->
            val m3u8Dir = uris[M3U8_DIR_URI_KSVP_KEY]?.let {
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
            }
            val musicDir = uris[MUSIC_DIR_URI_KSVP_KEY]?.let {
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
            }
            HomeScreenState(
                m3u8DirName = m3u8Dir?.name,
                m3u8Count = m3u8Dir?.listFiles()?.size ?: 0,
                musicDirName = musicDir?.name,
                lastAnalysisDateString = lastAnalysisMillis?.let {
                    SimpleDateFormat("HHmmss ddMMyy z", Locale.UK).format(Date(it))
                }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            HomeScreenState(null, 0, null, null)
        )
    // This pattern is better when there are multiple components listening in that should see
    // the same values. The combine code (hot flow) only runs once for all observers. If each
    // observer collected from a cold flow, the combine code runs once for each observer.
    // Courtesy of https://stackoverflow.com/a/66889741

    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())
    private val musicFoldersFlow = musicFolderRepository.getAllFlow()

    internal var analysisInProgress by mutableStateOf(false)
        private set
    internal var analysisProgress: Pair<Float, String>? by mutableStateOf(null)
        private set

    internal fun saveM3U8DirUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                keyStringValuePairRepository.put(M3U8_DIR_URI_KSVP_KEY to uri.toString())
            }
            applicationContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    internal fun saveMusicDirUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                keyStringValuePairRepository.put(MUSIC_DIR_URI_KSVP_KEY to uri.toString())
            }
            applicationContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    @SuppressLint("Range")
    internal fun analyseAllPlaylist() {
        analysisInProgress = true
        analysisProgress = Pair(0f, "Starting...")
        val startMillis = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            analysisProgress = Pair(0.1f, "Opening music directory...")
            val uris = urisFlow.first()
            val musicDirDF = uris[MUSIC_DIR_URI_KSVP_KEY]?.let {
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
            } ?: return@launch

            analysisProgress = Pair(0.2f, "Syncing music directory with database...")
            musicFolderRepository.ensureFoldersSane(
                musicDirDF.listFiles()
                    .filterNot { it.name!!.startsWith(".") }
                    .map { Pair(it.uri.toString(), it.name!!) }
            )

            analysisProgress = Pair(0.3f, "Unticking folders with more recent language changes...")
            val musicFolders = musicFoldersFlow.first()
            musicFolderRepository.automaticUntick(
                // Find all folders whose last modified is more recent than their doneMillis
                encodedUris = musicFolders
                    .filter { it.doneMillis != null && it.resetMillis == null }  // those that are ticked
                    .filter {
                        val cursor = applicationContext.contentResolver.query(
                            Uri.parse(it.encodedUri),
                            arrayOf(COLUMN_LAST_MODIFIED),
                            null, null, null
                        )
                        cursor?.use { c ->
                            c.moveToFirst() && (c.getLong(c.getColumnIndex(COLUMN_LAST_MODIFIED)) > it.doneMillis!!)
                        } ?: false
                    }.map { it.encodedUri },
                resetMillis = startMillis
            )

            analysisProgress = Pair(1f, "Finishing up...")
            keyStringValuePairRepository.put(
                LAST_ANALYSIS_MILLIS_KSVP_KEY to startMillis.toString()
            )
            delay(1000L)
            analysisInProgress = false
            analysisProgress = null
        }
    }

    internal fun openAudioPlayer(context: Context, uri: Uri) {
        // Mechanism documented @ https://developer.android.com/training/basics/intents/sending
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/mpeg")
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Define what your app should do if no activity can handle the intent.
            Log.e(TAG, "No music players exist on the device?")
        }
    }
}

private fun resolveUri(uri: Uri, appendUnencodedPath: String): Uri {
    return Uri.parse(
        uri.toString() + Uri.encode("/$appendUnencodedPath")
    )
}