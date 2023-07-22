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
import java.io.FileOutputStream
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
    internal fun analyseAllPlaylist(handleMessageFailure: (String) -> Unit) {
        analysisInProgress = true
        analysisProgress = Pair(0.01f, "Syncing music directory with database…")
        val startMillis = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val uris = urisFlow.first()
            val musicDirDF = DocumentFile.fromTreeUri(
                applicationContext,
                Uri.parse(uris[MUSIC_DIR_URI_KSVP_KEY]!!)  // non-null guaranteed by UI logic
            )!!  // non-null guaranteed by UI logic
            val m3u8DirDF = DocumentFile.fromTreeUri(
                applicationContext,
                Uri.parse(uris[M3U8_DIR_URI_KSVP_KEY])  // non-null guaranteed by UI logic
            )!!  // non-null guaranteed by UI logic

            musicFolderRepository.ensureFoldersSane(
                musicDirDF.listFiles()
                    .filterNot { it.name!!.startsWith(".") }
                    .map { Pair(it.uri.toString(), it.name!!) }
            )

            analysisProgress = Pair(0.05f, "Unticking folders with more recent language changes…")
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

            analysisProgress = Pair(0.1f, "[\"All.m3u8\"] Syncing…")
            val allM3U8DF = m3u8DirDF.findFile("All.m3u8")
            if (allM3U8DF == null) {
                handleMessageFailure("\"All.m3u8\" not found in M3U8 Directory")
                analysisInProgress = false
                analysisProgress = null
                return@launch
            }
            musicFileRepository.reset()
            val allLinePairLists = readM3U8WithRating(allM3U8DF, musicFolderNameToEncodedUri).let {
                musicFileRepository.ensurePresentFiles(it.musicFileList)
                it.linePairList
            }

            val songLinePairLists = mutableSetOf<Pair<String, String>>()

            listOf(
                "Songs - Choral.m3u8" to musicFileRepository::setLangCh,
                "Songs - CHN.m3u8" to musicFileRepository::setLangCN,
                "Songs - ENG.m3u8" to musicFileRepository::setLangEN,
                "Songs - JAP.m3u8" to musicFileRepository::setLangJP,
                "Songs - KOR.m3u8" to musicFileRepository::setLangKR,
                "Songs - Others.m3u8" to musicFileRepository::setLangO
            ).forEachIndexed { index, (m3u8FileName, musicFileRepositorySetLangFunction) ->
                analysisProgress = Pair(0.2f + index * 0.1f, "[\"$m3u8FileName\"] Syncing…")
                if (
                    !syncLanguage(
                        m3u8DirDF = m3u8DirDF,
                        m3u8FileName = m3u8FileName,
                        handleMessageFailure = handleMessageFailure,
                        musicFolderNameToEncodedUri = musicFolderNameToEncodedUri,
                        musicFileRepositorySetLangFunction = musicFileRepositorySetLangFunction,
                        songLinePairLists = songLinePairLists,
                    )
                ) {
                    analysisInProgress = false
                    analysisProgress = null
                    return@launch
                }
            }

            analysisProgress = Pair(0.8f, "Generating \"(Auto) Songs.m3u8\"…")
            if (
                !overwriteFile(
                    m3u8DirDF = m3u8DirDF,
                    m3u8FileName = "(Auto) Songs.m3u8",
                    handleMessageFailure = handleMessageFailure,
                    linesToWrite = songLinePairLists
                        .sortedBy { it.second.split("/").last() }  // sort by filename
                        .fold(mutableListOf<String>()) { acc, pair ->
                            acc.apply {
                                add(pair.first)
                                add(pair.second)
                            }
                        }.apply {
                            add(0, M3U8_HEADER_LINE)
                        }
                )
            ) {
                analysisInProgress = false
                analysisProgress = null
                return@launch
            }

            for (i in 0..5) {
                analysisProgress = Pair(0.825f + i * 0.025f, "Generating \"(Auto) ${i}S.m3u8\"…")
                if (
                    !overwriteFile(
                        m3u8DirDF = m3u8DirDF,
                        m3u8FileName = "(Auto) ${i}S.m3u8",
                        handleMessageFailure = handleMessageFailure,
                        linesToWrite = allLinePairLists
                            .filter { it.first.endsWith(i.digitToChar()) }
                            .sortedBy { it.second.split("/").last() }  // sort by filename
                            .fold(mutableListOf<String>()) { acc, pair ->
                                acc.apply {
                                    add(pair.first)
                                    add(pair.second)
                                }
                            }.apply {
                                add(0, M3U8_HEADER_LINE)
                            }
                    )
                ) {
                    analysisInProgress = false
                    analysisProgress = null
                    return@launch
                }
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
    ): M3U8ReadResultWithRating {
        val musicFileList = arrayListOf<MusicFile>()
        val linePairList = arrayListOf<Pair<String, String>>()  // without \n, due to readline()
        applicationContext.contentResolver.openInputStream(m3u8Df.uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine()  // Skip header line
                var line: String? = reader.readLine()
                while (line != null) {
                    val line1 = line
                    val rating = line.substringAfter("#EXT-X-RATING:").toInt()
                    line = reader.readLine()
                    val line2 = line
                    linePairList.add(Pair(line1, line2!!))

                    val musicFolderMusicFile = line.split("/").takeLast(2)
                    val musicFolderName = musicFolderMusicFile[0]
                    val musicFileName = musicFolderMusicFile[1]
                    val parentDirEncodedUri = musicFolderNameToEncodedUri[musicFolderName]!!
                    musicFileList.add(
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
        return M3U8ReadResultWithRating(
            musicFileList = musicFileList,
            linePairList = linePairList
        )
    }

    private fun readM3U8WithoutRating(
        m3u8Df: DocumentFile,
        musicFolderNameToEncodedUri: Map<String, String>
    ): M3U8ReadResultWithoutRating {
        val parentDirEncodedUriToMusicFileNamePairList = arrayListOf<Pair<String, String>>()
        val linePairList = arrayListOf<Pair<String, String>>()  // without \n, due to readline()
        applicationContext.contentResolver.openInputStream(m3u8Df.uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine()  // Skip header line
                var line: String? = reader.readLine()
                while (line != null) {
                    val line1 = line
                    line = reader.readLine()  // Only care about second of each group of two lines
                    val line2 = line
                    linePairList.add(Pair(line1, line2!!))

                    val musicFolderMusicFile = line.split("/").takeLast(2)
                    val musicFolderName = musicFolderMusicFile[0]
                    val musicFileName = musicFolderMusicFile[1]
                    val parentDirEncodedUri = musicFolderNameToEncodedUri[musicFolderName]!!
                    parentDirEncodedUriToMusicFileNamePairList.add(
                        Pair(parentDirEncodedUri, musicFileName)
                    )
                    line = reader.readLine()
                }
            }
        }
        return M3U8ReadResultWithoutRating(
            parentDirEncodedUriToMusicFileNamePairList = parentDirEncodedUriToMusicFileNamePairList,
            linePairList = linePairList
        )
    }

    /**
     * Returns `true` on success and `false` on failure
     * - `m3u8FileName` must terminate with .m3u8.
     */
    private suspend fun syncLanguage(
        m3u8DirDF: DocumentFile,
        m3u8FileName: String,
        handleMessageFailure: (String) -> Unit,
        musicFolderNameToEncodedUri: Map<String, String>,
        musicFileRepositorySetLangFunction: suspend (parentDirEncodedUri: String, fileName: String) -> Unit,
        songLinePairLists: MutableSet<Pair<String, String>>
    ): Boolean {
        val m3u8DF = m3u8DirDF.findFile(m3u8FileName)
        if (m3u8DF == null) {
            handleMessageFailure("\"$m3u8FileName\" not found in M3U8 Directory")
            return false
        }
        readM3U8WithoutRating(m3u8DF, musicFolderNameToEncodedUri).let { readResult ->
            readResult.parentDirEncodedUriToMusicFileNamePairList.forEach {
                musicFileRepositorySetLangFunction(it.first, it.second)
            }
            songLinePairLists.addAll(readResult.linePairList)
        }
        return true
    }

    /**
     * - `m3u8FileName` must terminate with .m3u8. Creates the .m3u8 file if it doesn't exist.
     * - There is no need to terminate each element in linesToWrite with "\n". That is handled
     * internally by this function.
     */
    private fun overwriteFile(
        m3u8DirDF: DocumentFile,
        m3u8FileName: String,
        handleMessageFailure: (String) -> Unit,
        linesToWrite: List<String>
    ): Boolean {
        val m3u8DF = m3u8DirDF.findFile(m3u8FileName)
            ?: m3u8DirDF.createFile("audio/x-mpegurl", m3u8FileName)
        if (m3u8DF == null) {
            handleMessageFailure("Failed to create $m3u8FileName")
            return false
        }
        applicationContext.contentResolver.openFileDescriptor(m3u8DF.uri, "wt")?.use {
            FileOutputStream(it.fileDescriptor).use { os ->
                linesToWrite
                    .forEach { line -> os.write("$line\n".toByteArray()) }
            }
        }
        return true
    }
}

private data class M3U8ReadResultWithRating(
    val musicFileList: List<MusicFile>,
    val linePairList: List<Pair<String, String>>
)

private data class M3U8ReadResultWithoutRating(
    val parentDirEncodedUriToMusicFileNamePairList: List<Pair<String, String>>,
    val linePairList: List<Pair<String, String>>
)

private const val M3U8_HEADER_LINE = "#EXTM3U"