package com.kaeonx.poweramphelper.ui.screens.home

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaeonx.poweramphelper.database.AppDatabase
import com.kaeonx.poweramphelper.database.KeyStringValuePairRepository
import com.kaeonx.poweramphelper.database.LAST_ANALYSIS_TS_KSVP_KEY
import com.kaeonx.poweramphelper.database.M3U8_DIR_URI_KSVP_KEY
import com.kaeonx.poweramphelper.database.MUSIC_DIR_URI_KSVP_KEY
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "homeScreenViewModel"

internal class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {
    // DONE: Other fields store reference to this leakable object;
    // It's OK, lasts till END of app. Problem is with activityContext.
    private val applicationContext = application

    private val appDatabaseInstance = AppDatabase.getInstance(applicationContext)
    private val keyStringValuePairRepository = KeyStringValuePairRepository(
        appDatabaseInstance.keyStringValuePairDao()
    )
    private val musicFolderRepository = MusicFolderRepository(appDatabaseInstance.musicFolderDao())
    private val musicFolders = musicFolderRepository.getAllFlow()

    private val uris =
        keyStringValuePairRepository.getFlow(listOf(M3U8_DIR_URI_KSVP_KEY, MUSIC_DIR_URI_KSVP_KEY))

    internal val homeScreenState = uris.map { map ->
        val m3u8Dir = map[M3U8_DIR_URI_KSVP_KEY]?.let {
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
        }
        val musicDir = map[MUSIC_DIR_URI_KSVP_KEY]?.let {
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
        }
        HomeScreenState(
            m3u8DirName = m3u8Dir?.name,
            m3u8Count = m3u8Dir?.listFiles()?.size ?: 0,
            musicDirName = musicDir?.name,
        )
    }

    internal val musicDirUri =
        keyStringValuePairRepository.getFlow(MUSIC_DIR_URI_KSVP_KEY).map { encodedUri ->
            encodedUri?.let { Uri.parse(it) }
        }

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
        val startMillis = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            uris.collect { urisMap ->
                val musicDirDF = urisMap[MUSIC_DIR_URI_KSVP_KEY]?.let {
                    DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
                } ?: return@collect

                musicFolderRepository.ensureFoldersSane(musicDirDF.listFiles().map {
                    Pair(
                        it.uri.toString(), it.name!!
                    )
                })

                musicFolders.collect { musicFoldersLs ->
                    // Find all folders whose last modified is more recent than their doneMillis
                    val toUntick = musicFoldersLs
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
                        }
                    musicFolderRepository.automaticUntick(
                        encodedUris = toUntick.map { it.encodedUri },
                        resetMillis = startMillis
                    )
                }
                keyStringValuePairRepository.put(
                    LAST_ANALYSIS_TS_KSVP_KEY to startMillis.toString()
                )
            }
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