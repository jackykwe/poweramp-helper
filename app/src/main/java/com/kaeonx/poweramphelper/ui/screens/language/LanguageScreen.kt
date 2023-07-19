package com.kaeonx.poweramphelper.ui.screens.language

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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

private val superscriptStyle = SpanStyle(
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    baselineShift = BaselineShift.Superscript
)

@Composable
internal fun LanguageScreen(languageScreenViewModel: LanguageScreenViewModel = viewModel()) {
    val languageScreenState by languageScreenViewModel.languageScreenState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(
            items = languageScreenState.musicFolders,
            key = { it.encodedUri }
        ) {
            ListItem(
                headlineContent = { Text(text = it.displayName) },
                supportingContent = {
                    Text(
                        text = buildAnnotatedString {
                            append("1")
                            withStyle(superscriptStyle) {
                                append("EN ")
                            }
                            append("1")
                            withStyle(superscriptStyle) {
                                append("CN ")
                            }
                            append("1")
                            withStyle(superscriptStyle) {
                                append("JP ")
                            }
                            append("1")
                            withStyle(superscriptStyle) {
                                append("KR ")
                            }
                            append("1")
                            withStyle(superscriptStyle) {
                                append("O ")
                            }
                            append("1")
                            withStyle(superscriptStyle) {
                                append("Ch")
                            }
                            append(" ⋅ ")
                            append("99")
                            withStyle(superscriptStyle) {
                                append("U ")
                            }
                        },
                        modifier = Modifier.alpha(0.66f),
                        fontSize = 12.sp
                    )
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