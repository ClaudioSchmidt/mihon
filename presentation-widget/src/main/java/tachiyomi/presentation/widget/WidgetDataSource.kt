package tachiyomi.presentation.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Defines what data the widget should display.
 */
enum class WidgetDataSourceType {
    /** Recently updated manga (default, existing behavior). */
    RECENT_UPDATES,

    /** All manga in the user's library. */
    LIBRARY,

    /** Manga from a specific category. */
    CATEGORY,
}

/**
 * Sort types available for the widget, mirroring [tachiyomi.domain.library.model.LibrarySort.Type].
 */
enum class WidgetSortType {
    Alphabetical,
    LastRead,
    LastUpdate,
    UnreadCount,
    TotalChapters,
    LatestChapter,
    ChapterFetchDate,
    DateAdded,
}

enum class WidgetSortDirection {
    Ascending,
    Descending,
}

object WidgetPreferenceKeys {
    val SOURCE_TYPE = stringPreferencesKey("widget_source_type")
    val CATEGORY_ID = longPreferencesKey("widget_category_id")

    // Filters (stored as TriState name: DISABLED / ENABLED_IS / ENABLED_NOT)
    val FILTER_DOWNLOADED = stringPreferencesKey("widget_filter_downloaded")
    val FILTER_UNREAD = stringPreferencesKey("widget_filter_unread")
    val FILTER_STARTED = stringPreferencesKey("widget_filter_started")
    val FILTER_BOOKMARKED = stringPreferencesKey("widget_filter_bookmarked")
    val FILTER_COMPLETED = stringPreferencesKey("widget_filter_completed")

    // Sort
    val SORT_TYPE = stringPreferencesKey("widget_sort_type")
    val SORT_DIRECTION = stringPreferencesKey("widget_sort_direction")

    // Layout
    val MERGE_LAST_TWO_ROWS = booleanPreferencesKey("widget_merge_last_two_rows")
}
