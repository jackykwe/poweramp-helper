package com.kaeonx.poweramphelper.ui.screens.language

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaeonx.poweramphelper.R
import com.kaeonx.poweramphelper.database.MusicFolderState
import com.kaeonx.poweramphelper.database.MusicFolderWithStatistics
import com.kaeonx.poweramphelper.utils.millisToDisplayWithoutTZ

private val superscriptStyle = SpanStyle(
    fontSize = 10.sp,
    fontWeight = FontWeight.Light,
    baselineShift = BaselineShift.Superscript
)

private val subscriptStyle = SpanStyle(
    fontSize = 10.sp,
    fontWeight = FontWeight.Light,
    baselineShift = BaselineShift.Subscript
)

private val greenStyle = SpanStyle(color = Color.Green)
private val redStyle = SpanStyle(color = Color.Red)

@Composable
internal fun LanguageScreen(languageScreenViewModel: LanguageScreenViewModel = viewModel()) {
    val languageScreenState by languageScreenViewModel.languageScreenState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(
            items = languageScreenState.musicFoldersWithStatistics,
            key = { it.encodedUri }
        ) {
            ListItem(
                headlineContent = { Text(text = it.dirName) },
                supportingContent = {
                    Column {
                        Text(
                            text = generateLangSumAnnotatedString(it),
                            modifier = Modifier.alpha(0.66f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        val timestampsReport = generateTimestampsReport(it)
                        AnimatedVisibility(visible = timestampsReport.isNotBlank()) {
                            Text(
                                text = timestampsReport,
                                modifier = Modifier.alpha(0.66f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = { languageScreenViewModel.userToggle(it) },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        if (it.state == MusicFolderState.DONE) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.baseline_check_circle_24),
                                contentDescription = null
                            )
                        } else {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.outline_pending_24),
                                contentDescription = null,
                                modifier = Modifier.alpha(0.25f)
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun generateLangSumAnnotatedString(stats: MusicFolderWithStatistics): AnnotatedString {
    return buildAnnotatedString {
        append(stats.langENSum.toString())
        withStyle(superscriptStyle) {
            append("EN  ")
        }
        append(stats.langCNSum.toString())
        withStyle(superscriptStyle) {
            append("CN  ")
        }
        append(stats.langJPSum.toString())
        withStyle(superscriptStyle) {
            append("JP  ")
        }
        append(stats.langKRSum.toString())
        withStyle(superscriptStyle) {
            append("KR  ")
        }
        append(stats.langOSum.toString())
        withStyle(superscriptStyle) {
            append("O  ")
        }
        append(stats.langChSum.toString())
        withStyle(superscriptStyle) {
            append("Ch")
        }
        append("\n")
        append(stats.langSongSum.toString())
        withStyle(subscriptStyle) {
            append("Song  ")
        }
        append((stats.fileCount - stats.langSongSum).toString())
        withStyle(subscriptStyle) {
            append("-  ")
        }
        append(stats.fileCount.toString())
        withStyle(subscriptStyle) {
            append("Σ  ")
        }
    }
}

private fun generateTimestampsReport(stats: MusicFolderWithStatistics): AnnotatedString {
    return buildAnnotatedString {
        when (stats.state) {
            MusicFolderState.NOT_DONE -> {}
            MusicFolderState.DONE -> {
                withStyle(greenStyle) {
                    append("(done) ")
                    append(millisToDisplayWithoutTZ(stats.doneMillis!!))
                }
            }

            MusicFolderState.DONE_AUTO_RESET -> {
                withStyle(redStyle) {
                    append("(done) ")
                    append(millisToDisplayWithoutTZ(stats.doneMillis!!))
                    append("\n(reset) ")
                    append(millisToDisplayWithoutTZ(stats.resetMillis!!))
                }
            }
        }
    }
}