package com.kaeonx.poweramphelper.ui.screens.language

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kaeonx.poweramphelper.R
import com.kaeonx.poweramphelper.database.MusicFolderState
import com.kaeonx.poweramphelper.ui.PHDestinationHidden
import com.kaeonx.poweramphelper.utils.mapToIntListString

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LanguageScreen(
    navController: NavController,
    languageScreenViewModel: LanguageScreenViewModel = viewModel()
) {
    val languageScreenState by languageScreenViewModel.languageScreenState.collectAsStateWithLifecycle()

    // Dialog and Radio stuffs
    var dialogOpen by remember { mutableStateOf(false) }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Scroll Position: ", modifier = Modifier.alpha(0.5f))
            Text(text = scrollPercentage, modifier = Modifier.alpha(0.5f))
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { dialogOpen = true }) {
                Text(text = languageScreenState.sortString)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            items(
                items = languageScreenState.musicFoldersWithLangStats,
                key = { it.encodedUri }
            ) {
                ListItem(
                    headlineContent = { Text(text = it.dirName) },
                    modifier = Modifier
                        .animateItemPlacement()
                        .clickable {
                            navController.navigate(
                                PHDestinationHidden.LanguageFolder.resolveRoute(
                                    mapToIntListString(it.encodedUri)
                                )
                            )
                        },
                    supportingContent = {
                        Column {
                            Text(
                                text = it.countReport,
                                modifier = Modifier.alpha(0.66f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            val timestampsReport = it.timestampsReport
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
    if (dialogOpen) {
        val (pendingFirstSort, onPendingFirstSortChanged) = remember {
            mutableStateOf(languageScreenState.pendingFirstSort)
        }  // Higher priority
        val (descendingSort, onDescendingSortChanged) = remember {
            mutableStateOf(languageScreenState.descendingSort)
        }
        val (sortOptionSelected, onSortOptionSelected) = remember {
            mutableStateOf(languageScreenState.sortOption)
        }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    languageScreenViewModel.saveSortOptions(
                        sortOption = sortOptionSelected,
                        pendingFirstSort = pendingFirstSort,
                        descendingSort = descendingSort
                    )
                    dialogOpen = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            title = { Text(text = "Sort By") },
            text = {
                Column {
                    Column(Modifier.selectableGroup()) {
                        LanguageScreenSortOption.values().forEach { sortOption ->
                            val display = sortOption.display
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .selectable(
                                        selected = (display == sortOptionSelected.display),
                                        onClick = { onSortOptionSelected(sortOption) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (display == sortOptionSelected.display),
                                    onClick = null // null recommended for accessibility with screenreaders
                                )
                                Text(
                                    text = display,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .toggleable(
                                value = pendingFirstSort,
                                onValueChange = { onPendingFirstSortChanged(!pendingFirstSort) },
                                role = Role.Checkbox
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = pendingFirstSort,
                            onCheckedChange = null // null recommended for accessibility with screenreaders
                        )
                        Text(
                            text = "Pending First",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .toggleable(
                                value = descendingSort,
                                onValueChange = { onDescendingSortChanged(!descendingSort) },
                                role = Role.Checkbox
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = descendingSort,
                            onCheckedChange = null // null recommended for accessibility with screenreaders
                        )
                        Text(
                            text = "Descending",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            },
        )
    }
}