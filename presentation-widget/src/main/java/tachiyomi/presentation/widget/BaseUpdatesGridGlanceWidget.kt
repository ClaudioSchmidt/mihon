package tachiyomi.presentation.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.presentation.widget.components.LockedWidget
import tachiyomi.presentation.widget.components.UpdatesWidget
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import tachiyomi.presentation.widget.util.calculateGridLayout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseUpdatesGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val ids = manager.getGlanceIds(javaClass)
        val grid = ids
            .flatMap { manager.getAppWidgetSizes(it) }
            .maxBy { it.height.value * it.width.value }
            .calculateGridLayout(topPadding, bottomPadding)

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }

            // Read per-widget configuration reactively — currentState() recomposes whenever
            // updateAppWidgetState() writes new prefs, so config changes apply immediately
            // without needing to restart the app or wait for a periodic refresh.
            val prefs = currentState<Preferences>()
            val sourceType = try {
                WidgetDataSourceType.valueOf(
                    prefs[WidgetPreferenceKeys.SOURCE_TYPE] ?: WidgetDataSourceType.RECENT_UPDATES.name,
                )
            } catch (_: IllegalArgumentException) {
                WidgetDataSourceType.RECENT_UPDATES
            }
            val categoryId = prefs[WidgetPreferenceKeys.CATEGORY_ID] ?: 0L

            val filterDownloaded = parseTriState(prefs[WidgetPreferenceKeys.FILTER_DOWNLOADED])
            val filterUnread = parseTriState(prefs[WidgetPreferenceKeys.FILTER_UNREAD])
            val filterStarted = parseTriState(prefs[WidgetPreferenceKeys.FILTER_STARTED])
            val filterBookmarked = parseTriState(prefs[WidgetPreferenceKeys.FILTER_BOOKMARKED])
            val filterCompleted = parseTriState(prefs[WidgetPreferenceKeys.FILTER_COMPLETED])

            val sortType = try {
                WidgetSortType.valueOf(prefs[WidgetPreferenceKeys.SORT_TYPE] ?: WidgetSortType.Alphabetical.name)
            } catch (_: IllegalArgumentException) {
                WidgetSortType.Alphabetical
            }
            val sortDirection = try {
                WidgetSortDirection.valueOf(
                    prefs[WidgetPreferenceKeys.SORT_DIRECTION] ?: WidgetSortDirection.Ascending.name,
                )
            } catch (_: IllegalArgumentException) {
                WidgetSortDirection.Ascending
            }

            val mergeLastTwoRows = prefs[WidgetPreferenceKeys.MERGE_LAST_TWO_ROWS] ?: false

            val flow = remember(sourceType, categoryId, filterDownloaded, filterUnread, filterStarted, filterBookmarked, filterCompleted, sortType, sortDirection, mergeLastTwoRows) {
                getDataFlow(
                    sourceType, categoryId,
                    filterDownloaded, filterUnread, filterStarted, filterBookmarked, filterCompleted,
                    sortType, sortDirection,
                ).map { rawData ->
                    rawData.prepareData(grid.rowCount, grid.columnCount, grid.coverWidth, grid.coverHeight)
                }
            }
            val data by flow.collectAsState(initial = null)
            UpdatesWidget(
                data = data,
                contentColor = foreground,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                appWidgetId = appWidgetId,
                mergeLastTwoRows = mergeLastTwoRows,
                modifier = containerModifier,
            )
        }
    }

    private fun getDataFlow(
        sourceType: WidgetDataSourceType,
        categoryId: Long,
        filterDownloaded: TriState,
        filterUnread: TriState,
        filterStarted: TriState,
        filterBookmarked: TriState,
        filterCompleted: TriState,
        sortType: WidgetSortType,
        sortDirection: WidgetSortDirection,
    ): Flow<List<Pair<Long, MangaCover>>> {
        return when (sourceType) {
            WidgetDataSourceType.RECENT_UPDATES -> {
                getUpdates
                    .subscribe(false, DateLimit.toEpochMilli())
                    .map { updates ->
                        updates.distinctBy { it.mangaId }
                            .map { it.mangaId to it.coverData }
                    }
            }
            WidgetDataSourceType.LIBRARY -> {
                getLibraryManga.subscribe()
                    .map { list ->
                        list.applyWidgetFilters(
                            filterDownloaded, filterUnread, filterStarted,
                            filterBookmarked, filterCompleted,
                        )
                            .applyWidgetSort(sortType, sortDirection)
                            .map { it.manga.id to it.manga.asMangaCover() }
                    }
            }
            WidgetDataSourceType.CATEGORY -> {
                getLibraryManga.subscribe()
                    .map { list ->
                        list.filter { categoryId in it.categories }
                            .applyWidgetFilters(
                                filterDownloaded, filterUnread, filterStarted,
                                filterBookmarked, filterCompleted,
                            )
                            .applyWidgetSort(sortType, sortDirection)
                            .map { it.manga.id to it.manga.asMangaCover() }
                    }
            }
        }
    }

    private fun List<LibraryManga>.applyWidgetFilters(
        filterDownloaded: TriState,
        filterUnread: TriState,
        filterStarted: TriState,
        filterBookmarked: TriState,
        filterCompleted: TriState,
    ): List<LibraryManga> {
        return filter { item ->
            applyFilter(filterUnread) { item.unreadCount > 0 } &&
                applyFilter(filterStarted) { item.hasStarted } &&
                applyFilter(filterBookmarked) { item.hasBookmarks } &&
                applyFilter(filterCompleted) { item.manga.status.toInt() == MANGA_STATUS_COMPLETED } &&
                applyFilter(filterDownloaded) { item.manga.source == LOCAL_SOURCE_ID }
        }
    }

    private fun List<LibraryManga>.applyWidgetSort(
        sortType: WidgetSortType,
        sortDirection: WidgetSortDirection,
    ): List<LibraryManga> {
        val comparator = when (sortType) {
            WidgetSortType.Alphabetical -> compareBy<LibraryManga> { it.manga.title }
            WidgetSortType.LastRead -> compareBy { it.lastRead }
            WidgetSortType.LastUpdate -> compareBy { it.manga.lastUpdate }
            WidgetSortType.UnreadCount -> compareBy { it.unreadCount }
            WidgetSortType.TotalChapters -> compareBy { it.totalChapters }
            WidgetSortType.LatestChapter -> compareBy { it.latestUpload }
            WidgetSortType.ChapterFetchDate -> compareBy { it.chapterFetchedAt }
            WidgetSortType.DateAdded -> compareBy { it.manga.dateAdded }
        }
        val finalComparator = if (sortDirection == WidgetSortDirection.Descending) {
            comparator.reversed()
        } else {
            comparator
        }
        return sortedWith(finalComparator)
    }

    private fun parseTriState(value: String?): TriState {
        return try {
            TriState.valueOf(value ?: TriState.DISABLED.name)
        } catch (_: IllegalArgumentException) {
            TriState.DISABLED
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun List<Pair<Long, MangaCover>>.prepareData(
        rowCount: Int,
        columnCount: Int,
        coverWidth: Dp,
        coverHeight: Dp,
    ): ImmutableList<Pair<Long, Bitmap?>> {
        // Resize to adaptive cover size
        val widthPx = coverWidth.value.toInt().dpToPx
        val heightPx = coverHeight.value.toInt().dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        return withIOContext {
            this@prepareData
                .take(rowCount * columnCount)
                .map { (mangaId, cover) ->
                    async {
                        val request = ImageRequest.Builder(context)
                            .data(cover)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .precision(Precision.EXACT)
                            .size(widthPx, heightPx)
                            .scale(Scale.FILL)
                            .let {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                    it.transformations(RoundedCornersTransformation(roundPx))
                                } else {
                                    it // Handled by system
                                }
                            }
                            .build()
                        val bitmap = context.imageLoader.executeBlocking(request)
                            .image
                            ?.asDrawable(context.resources)
                            ?.toBitmap()
                        Pair(mangaId, bitmap)
                    }
                }
                .awaitAll()
                .toImmutableList()
        }
    }

    companion object {
        private const val LOCAL_SOURCE_ID = 0L
        private const val MANGA_STATUS_COMPLETED = 2
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
