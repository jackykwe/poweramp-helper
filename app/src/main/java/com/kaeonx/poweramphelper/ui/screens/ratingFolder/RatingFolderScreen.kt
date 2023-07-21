package com.kaeonx.poweramphelper.ui.screens.ratingFolder

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaeonx.poweramphelper.utils.openAudioPlayer
import com.kaeonx.poweramphelper.utils.resolveUri

@Composable
internal fun RatingFolderScreen(
    encodedFolderUri: String,
    ratingFolderScreenViewModel: RatingFolderScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val ratingFolderScreenState by ratingFolderScreenViewModel.ratingFolderScreenState.collectAsStateWithLifecycle()

    // Scroll position stuffs
    val listState = rememberLazyListState()
    val scrollPercentage by remember {
        derivedStateOf {
            val pct = (listState.firstVisibleItemIndex * 100).div(
                maxOf(
                    1,
                    listState.layoutInfo.let {
                        it.totalItemsCount - it.visibleItemsInfo.size
                    }
                )
            )
            "$pct%"
        }
    }

    Column(modifier = Modifier.padding(8.dp)) {
        Row(modifier = Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Scroll Position: ", modifier = Modifier.alpha(0.5f))
            Text(text = scrollPercentage, modifier = Modifier.alpha(0.5f))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = ratingFolderScreenState.dirName,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            items(
                items = ratingFolderScreenState.musicFiles,
                key = { it.fileName }
            ) {
                ListItem(
                    headlineContent = { Text(text = it.fileName) },
                    modifier = Modifier.clickable {
                        openAudioPlayer(
                            context,
                            resolveUri(Uri.parse(it.parentDirEncodedUri), it.fileName)
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = "零",
                                modifier = Modifier.alpha(0.66f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                )
            }
        }
    }
}