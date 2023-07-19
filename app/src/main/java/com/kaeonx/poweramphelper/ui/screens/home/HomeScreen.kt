package com.kaeonx.poweramphelper.ui.screens.home

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaeonx.poweramphelper.LocalSnackbarHostState
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val TAG = "HomeScreen"

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun HomeScreen(homeScreenViewModel: HomeScreenViewModel = viewModel()) {
    val context = LocalContext.current
    val homeScreenState by homeScreenViewModel.homeScreenState.collectAsStateWithLifecycle()

    // Originally courtesy of https://stackoverflow.com/a/67156998
    // https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary#rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContract,kotlin.Function1)
    val musicDirSelector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { homeScreenViewModel.saveMusicDirUri(it) }
        }
    val m3u8DirSelector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { homeScreenViewModel.saveM3U8DirUri(it) }
        }

    // Snackbar stuff
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

//    // Progress bar stuff
    val animatedProgress by animateFloatAsState(
        targetValue = homeScreenViewModel.analysisProgress?.first ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

//    Log.i(TAG, "${m3u8Dir.toString()}")
//    Log.i(TAG, "${m3u8Dir?.listFiles().toString()}")
//    Log.i(TAG, "${m3u8Dir?.listFiles()?.size}")
//    m3u8Dir?.listFiles()?.forEach { f ->
//        Log.i(TAG, f.name ?: "????")
//        Log.e(TAG, readTextFromUri(context.contentResolver, f.uri))
//    }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                val musicDirName = homeScreenState.musicDirName
                Text(
                    text = "Music Directory",
                    fontWeight = FontWeight.Bold
                )
                if (musicDirName == null) {
                    Column {
                        Text(text = "No music directory selected")
                        Button(
                            onClick = { musicDirSelector.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Select music directory")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = musicDirName)
                        }
                        Button(onClick = { musicDirSelector.launch(null) }) {
                            Text(text = "Change")
                        }
                    }
                }
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                val m3u8DirName = homeScreenState.m3u8DirName
                Text(
                    text = "M3U8 Directory",
                    fontWeight = FontWeight.Bold
                )
                if (m3u8DirName == null) {
                    Column {
                        Text(text = "No M3U8 directory selected")
                        Button(
                            onClick = { m3u8DirSelector.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Select M3U8 directory")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = m3u8DirName)
                            Text(
                                text = "(${homeScreenState.m3u8Count} .m3u8 files)",
                                fontStyle = FontStyle.Italic
                            )
                        }
                        Button(onClick = { m3u8DirSelector.launch(null) }) {
                            Text(text = "Change")
                        }
                    }
                }
            }
        }

        if (homeScreenState.musicDirName != null && homeScreenState.m3u8DirName != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Analysis",
                        fontWeight = FontWeight.Bold
                    )
                    if (homeScreenState.lastAnalysisDateString != null) {
                        Text(
                            text = "Last performed at ${homeScreenState.lastAnalysisDateString}",
                            fontStyle = FontStyle.Italic
                        )
                    }
                    AnimatedContent(targetState = homeScreenViewModel.analysisInProgress) { aip ->
                        if (aip) {
                            Column {
                                LinearProgressIndicator(
                                    progress = animatedProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                                homeScreenViewModel.analysisProgress?.second?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { homeScreenViewModel.analyseAllPlaylist() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Analyse")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Throws(IOException::class)
private fun readTextFromUri(contentResolver: ContentResolver, uri: Uri): String {
    val stringBuilder = StringBuilder()
    contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = reader.readLine()
            }
        }
    }
    return stringBuilder.toString()
}
