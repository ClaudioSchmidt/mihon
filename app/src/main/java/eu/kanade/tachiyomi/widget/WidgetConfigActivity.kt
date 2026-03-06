package eu.kanade.tachiyomi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import eu.kanade.presentation.theme.TachiyomiTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.presentation.widget.UpdatesGridGlanceWidget
import tachiyomi.presentation.widget.WidgetDataSourceType
import tachiyomi.presentation.widget.WidgetPreferenceKeys
import tachiyomi.presentation.widget.WidgetSortDirection
import tachiyomi.presentation.widget.WidgetSortType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WidgetConfigActivity : ComponentActivity() {

    private val getCategories: GetCategories = Injekt.get()
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            TachiyomiTheme {
                val categories by getCategories.subscribe().collectAsState(initial = emptyList())

                var sourceType by remember { mutableStateOf(WidgetDataSourceType.RECENT_UPDATES) }
                var categoryId by remember { mutableStateOf(0L) }

                var filterDownloaded by remember { mutableStateOf(TriState.DISABLED) }
                var filterUnread by remember { mutableStateOf(TriState.DISABLED) }
                var filterStarted by remember { mutableStateOf(TriState.DISABLED) }
                var filterBookmarked by remember { mutableStateOf(TriState.DISABLED) }
                var filterCompleted by remember { mutableStateOf(TriState.DISABLED) }

                var sortType by remember { mutableStateOf(WidgetSortType.Alphabetical) }
                var sortDirection by remember { mutableStateOf(WidgetSortDirection.Ascending) }
                var mergeLastTwoRows by remember { mutableStateOf(false) }

                // Load existing widget preferences when reconfiguring
                LaunchedEffect(appWidgetId) {
                    try {
                        val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                        val glanceId = manager.getGlanceIdBy(appWidgetId)
                        val state = getAppWidgetState(
                            this@WidgetConfigActivity,
                            PreferencesGlanceStateDefinition,
                            glanceId,
                        )
                        state[WidgetPreferenceKeys.SOURCE_TYPE]?.let { name ->
                            try { sourceType = WidgetDataSourceType.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.CATEGORY_ID]?.let { categoryId = it }
                        state[WidgetPreferenceKeys.FILTER_DOWNLOADED]?.let { name ->
                            try { filterDownloaded = TriState.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.FILTER_UNREAD]?.let { name ->
                            try { filterUnread = TriState.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.FILTER_STARTED]?.let { name ->
                            try { filterStarted = TriState.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.FILTER_BOOKMARKED]?.let { name ->
                            try { filterBookmarked = TriState.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.FILTER_COMPLETED]?.let { name ->
                            try { filterCompleted = TriState.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.SORT_TYPE]?.let { name ->
                            try { sortType = WidgetSortType.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        state[WidgetPreferenceKeys.SORT_DIRECTION]?.let { name ->
                            try { sortDirection = WidgetSortDirection.valueOf(name) } catch (_: IllegalArgumentException) {}
                        }
                        mergeLastTwoRows = state[WidgetPreferenceKeys.MERGE_LAST_TWO_ROWS] ?: false
                    } catch (_: Exception) {
                        // New widget — use defaults
                    }
                }

                val showFiltersAndSort = sourceType != WidgetDataSourceType.RECENT_UPDATES

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Configure widget") })
                    },
                    bottomBar = {
                        Button(
                            onClick = {
                                saveAndFinish(
                                    appWidgetId, sourceType, categoryId,
                                    filterDownloaded, filterUnread, filterStarted,
                                    filterBookmarked, filterCompleted,
                                    sortType, sortDirection,
                                    mergeLastTwoRows,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text("Confirm")
                        }
                    },
                ) { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        // --- Source selection ---
                        item {
                            SectionHeader("Data source")
                        }
                        item {
                            SourceRadioItem(
                                icon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
                                title = "Recent updates",
                                selected = sourceType == WidgetDataSourceType.RECENT_UPDATES,
                                onClick = {
                                    sourceType = WidgetDataSourceType.RECENT_UPDATES
                                    categoryId = 0L
                                },
                            )
                        }
                        item {
                            SourceRadioItem(
                                icon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                                title = "Library",
                                selected = sourceType == WidgetDataSourceType.LIBRARY,
                                onClick = {
                                    sourceType = WidgetDataSourceType.LIBRARY
                                    categoryId = 0L
                                },
                            )
                        }
                        items(categories) { category ->
                            SourceRadioItem(
                                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                title = if (category.isSystemCategory) "Uncategorized" else category.name,
                                selected = sourceType == WidgetDataSourceType.CATEGORY && categoryId == category.id,
                                onClick = {
                                    sourceType = WidgetDataSourceType.CATEGORY
                                    categoryId = category.id
                                },
                            )
                        }

                        // --- Layout ---
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            SectionHeader("Layout")
                        }
                        item {
                            BooleanToggleItem(
                                label = "Merge last two rows (taller covers at bottom)",
                                checked = mergeLastTwoRows,
                                onClick = { mergeLastTwoRows = !mergeLastTwoRows },
                            )
                        }

                        if (showFiltersAndSort) {
                            // --- Filters ---
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SectionHeader("Filter")
                            }
                            item {
                                TriStateFilterItem(
                                    label = "Downloaded",
                                    state = filterDownloaded,
                                    onClick = { filterDownloaded = filterDownloaded.next() },
                                )
                            }
                            item {
                                TriStateFilterItem(
                                    label = "Unread",
                                    state = filterUnread,
                                    onClick = { filterUnread = filterUnread.next() },
                                )
                            }
                            item {
                                TriStateFilterItem(
                                    label = "Started",
                                    state = filterStarted,
                                    onClick = { filterStarted = filterStarted.next() },
                                )
                            }
                            item {
                                TriStateFilterItem(
                                    label = "Bookmarked",
                                    state = filterBookmarked,
                                    onClick = { filterBookmarked = filterBookmarked.next() },
                                )
                            }
                            item {
                                TriStateFilterItem(
                                    label = "Completed",
                                    state = filterCompleted,
                                    onClick = { filterCompleted = filterCompleted.next() },
                                )
                            }

                            // --- Sort ---
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SectionHeader("Sort")
                            }
                            val sortOptions = listOf(
                                "Alphabetical" to WidgetSortType.Alphabetical,
                                "Last read" to WidgetSortType.LastRead,
                                "Last update" to WidgetSortType.LastUpdate,
                                "Unread count" to WidgetSortType.UnreadCount,
                                "Total chapters" to WidgetSortType.TotalChapters,
                                "Latest chapter" to WidgetSortType.LatestChapter,
                                "Chapter fetch date" to WidgetSortType.ChapterFetchDate,
                                "Date added" to WidgetSortType.DateAdded,
                            )
                            items(sortOptions) { (label, type) ->
                                SortOptionItem(
                                    label = label,
                                    selected = sortType == type,
                                    sortDescending = if (sortType == type) {
                                        sortDirection == WidgetSortDirection.Descending
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        if (sortType == type) {
                                            // Toggle direction
                                            sortDirection = if (sortDirection == WidgetSortDirection.Ascending) {
                                                WidgetSortDirection.Descending
                                            } else {
                                                WidgetSortDirection.Ascending
                                            }
                                        } else {
                                            sortType = type
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinish(
        appWidgetId: Int,
        sourceType: WidgetDataSourceType,
        categoryId: Long,
        filterDownloaded: TriState,
        filterUnread: TriState,
        filterStarted: TriState,
        filterBookmarked: TriState,
        filterCompleted: TriState,
        sortType: WidgetSortType,
        sortDirection: WidgetSortDirection,
        mergeLastTwoRows: Boolean,
    ) {
        scope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@WidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId,
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(WidgetPreferenceKeys.SOURCE_TYPE, sourceType.name)
                    set(WidgetPreferenceKeys.CATEGORY_ID, categoryId)
                    set(WidgetPreferenceKeys.FILTER_DOWNLOADED, filterDownloaded.name)
                    set(WidgetPreferenceKeys.FILTER_UNREAD, filterUnread.name)
                    set(WidgetPreferenceKeys.FILTER_STARTED, filterStarted.name)
                    set(WidgetPreferenceKeys.FILTER_BOOKMARKED, filterBookmarked.name)
                    set(WidgetPreferenceKeys.FILTER_COMPLETED, filterCompleted.name)
                    set(WidgetPreferenceKeys.SORT_TYPE, sortType.name)
                    set(WidgetPreferenceKeys.SORT_DIRECTION, sortDirection.name)
                    set(WidgetPreferenceKeys.MERGE_LAST_TWO_ROWS, mergeLastTwoRows)
                }
            }

            UpdatesGridGlanceWidget().update(this@WidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun BooleanToggleItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SourceRadioItem(
    icon: @Composable () -> Unit,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun TriStateFilterItem(
    label: String,
    state: TriState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = when (state) {
                TriState.DISABLED -> Icons.Rounded.CheckBoxOutlineBlank
                TriState.ENABLED_IS -> Icons.Rounded.CheckBox
                TriState.ENABLED_NOT -> Icons.Rounded.DisabledByDefault
            },
            contentDescription = null,
            tint = if (state == TriState.DISABLED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SortOptionItem(
    label: String,
    selected: Boolean,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        val arrowIcon = when (sortDescending) {
            true -> Icons.Default.ArrowDownward
            false -> Icons.Default.ArrowUpward
            null -> null
        }
        if (arrowIcon != null) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
