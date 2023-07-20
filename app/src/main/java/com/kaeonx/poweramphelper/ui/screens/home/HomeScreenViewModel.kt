package com.kaeonx.poweramphelper.ui.screens.home

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
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
import com.kaeonx.poweramphelper.database.MusicFile
import com.kaeonx.poweramphelper.database.MusicFileRepository
import com.kaeonx.poweramphelper.database.MusicFolderRepository
import com.kaeonx.poweramphelper.utils.millisToDisplayWithTZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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
                lastAnalysisDateString = lastAnalysisMillis?.let { millisToDisplayWithTZ(it) }
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

    private val musicFileRepository = MusicFileRepository(appDatabaseInstance.musicFileDao())

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
        // TODO: Handle nulls properly; they shldn't be there
        // TODO: Reorganise code, this is monolithic
        analysisInProgress = true
        analysisProgress = Pair(0f, "Starting…")
        val startMillis = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val uris = urisFlow.first()
            val musicDirDF = uris[MUSIC_DIR_URI_KSVP_KEY]?.let {
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
            } ?: return@launch

            analysisProgress = Pair(0.1f, "Syncing music directory with database…")
            musicFolderRepository.ensureFoldersSane(
                musicDirDF.listFiles()
                    .filterNot { it.name!!.startsWith(".") }
                    .map { Pair(it.uri.toString(), it.name!!) }
            )

            analysisProgress = Pair(0.2f, "Unticking folders with more recent language changes…")
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

            val musicFolderNameToEncodedUri = musicFolders.associate { it.dirName to it.encodedUri }
            val m3u8DirDF = uris[M3U8_DIR_URI_KSVP_KEY]?.let {
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(it))
            } ?: return@launch

            analysisProgress = Pair(0.3f, "[\"All.m3u8\"] Reading…")
            m3u8DirDF.findFile("All.m3u8")?.let { df ->
                musicFileRepository.reset()
                val presentFiles = readM3U8WithRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.35f, "[\"All.m3u8\"] Syncing with database…")
                musicFileRepository.ensurePresentFiles(presentFiles)
            }

            analysisProgress = Pair(0.4f, "[\"Songs - Choral.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - Choral.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.45f, "[\"Songs - Choral.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangCh(it.first, it.second) }
            }

            analysisProgress = Pair(0.5f, "[\"Songs - CHN.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - CHN.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.55f, "[\"Songs - CHN.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangCN(it.first, it.second) }
            }

            analysisProgress = Pair(0.6f, "[\"Songs - ENG.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - ENG.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.65f, "[\"Songs - ENG.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangEN(it.first, it.second) }
            }

            analysisProgress = Pair(0.7f, "[\"Songs - JAP.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - JAP.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.75f, "[\"Songs - JAP.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangJP(it.first, it.second) }
            }

            analysisProgress = Pair(0.8f, "[\"Songs - KOR.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - KOR.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.85f, "[\"Songs - KOR.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangKR(it.first, it.second) }
            }

            analysisProgress = Pair(0.9f, "[\"Songs - Others.m3u8\"] Reading…")
            m3u8DirDF.findFile("Songs - Others.m3u8")?.let { df ->
                val langFiles = readM3U8WithoutRating(df, musicFolderNameToEncodedUri)
                analysisProgress = Pair(0.95f, "[\"Songs - Others.m3u8\"] Syncing with database…")
                langFiles.forEach { musicFileRepository.setLangO(it.first, it.second) }
            }

            analysisProgress = Pair(1f, "Finishing up…")
            keyStringValuePairRepository.put(
                LAST_ANALYSIS_MILLIS_KSVP_KEY to startMillis.toString()
            )
            delay(1000L)
            analysisInProgress = false
            analysisProgress = null
        }
    }

    private fun readM3U8WithRating(
        m3u8Df: DocumentFile,
        musicFolderNameToEncodedUri: Map<String, String>
    ): List<MusicFile> {
        val result = arrayListOf<MusicFile>()
        applicationContext.contentResolver.openInputStream(m3u8Df.uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine()  // Skip first line
                var line: String? = reader.readLine()
                while (line != null) {
                    val rating = line.substringAfter("#EXT-X-RATING:").toInt()
                    line = reader.readLine()
                    val musicFolderMusicFile = line.split("/").takeLast(2)
                    val musicFolderName = musicFolderMusicFile[0]
                    val musicFileName = musicFolderMusicFile[1]
                    val parentDirEncodedUri = musicFolderNameToEncodedUri[musicFolderName]!!
                    result.add(
                        MusicFile(
                            parentDirEncodedUri = parentDirEncodedUri,
                            fileName = musicFileName,
                            rating = rating
                        )
                    )
                    line = reader.readLine()
                }
            }
        }
        return result
    }

    private fun readM3U8WithoutRating(
        m3u8Df: DocumentFile,
        musicFolderNameToEncodedUri: Map<String, String>
    ): List<Pair<String, String>> {
        val result = arrayListOf<Pair<String, String>>()
        applicationContext.contentResolver.openInputStream(m3u8Df.uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine()  // Skip first line
                var line: String? = reader.readLine()
                while (line != null) {
                    line = reader.readLine()  // Only care about second of each group of two lines
                    val musicFolderMusicFile = line.split("/").takeLast(2)
                    val musicFolderName = musicFolderMusicFile[0]
                    val musicFileName = musicFolderMusicFile[1]
                    val parentDirEncodedUri = musicFolderNameToEncodedUri[musicFolderName]!!
                    result.add(Pair(parentDirEncodedUri, musicFileName))
                    line = reader.readLine()
                }
            }
        }
        return result
    }
}