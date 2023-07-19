package com.kaeonx.poweramphelper.ui.screens.home

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val TAG = "HomeScreen"

@Composable
internal fun HomeScreen(homeScreenViewModel: HomeScreenViewModel = viewModel()) {
    val context = LocalContext.current
    val homeScreenState by homeScreenViewModel.homeScreenState.collectAsStateWithLifecycle(
        initialValue = HomeScreenState(null, 0, null)
    )

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

//    Log.i(TAG, "${m3u8Dir.toString()}")
//    Log.i(TAG, "${m3u8Dir?.listFiles().toString()}")
//    Log.i(TAG, "${m3u8Dir?.listFiles()?.size}")
//    m3u8Dir?.listFiles()?.forEach { f ->
//        Log.i(TAG, f.name ?: "????")
//        Log.e(TAG, readTextFromUri(context.contentResolver, f.uri))
//    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
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
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
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

        if (homeScreenState.m3u8DirName != null) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Analysis",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Last performed at /*TODO*/",
                        fontStyle = FontStyle.Italic
                    )
                    Button(
                        onClick = { homeScreenViewModel.analyseAllPlaylist() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Analyse")
                    }
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "kaboom")
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
