package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.updates.interactor.GetUpdates

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val getLibraryManga: GetLibraryManga,
    private val securityPreferences: SecurityPreferences,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        combine(
            getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli()),
            getLibraryManga.subscribe(),
            securityPreferences.useAuthenticator().changes(),
            transform = { updates, library, locked -> Triple(updates, library, locked) },
        )
            // Coalesce rapid consecutive updates to avoid flooding widget updates
            .debounce(500)
            // Use a cheaper comparator to avoid allocations when comparing large lists.
            // Compute lightweight checksums instead of building sets and hashing entire lists.
            .distinctUntilChanged { old, new ->
                // compare locked state first
                if (old.third != new.third) return@distinctUntilChanged false

                val oldUpdatesXor = old.first.fold(0L) { acc, u -> acc xor u.chapterId }
                val newUpdatesXor = new.first.fold(0L) { acc, u -> acc xor u.chapterId }
                if (oldUpdatesXor != newUpdatesXor) return@distinctUntilChanged false

                val oldLibIdsXor = old.second.fold(0L) { acc, l -> acc xor l.manga.id }
                val newLibIdsXor = new.second.fold(0L) { acc, l -> acc xor l.manga.id }
                if (oldLibIdsXor != newLibIdsXor) return@distinctUntilChanged false

                val oldLastReadXor = old.second.fold(0L) { acc, l -> acc xor l.lastRead }
                val newLastReadXor = new.second.fold(0L) { acc, l -> acc xor l.lastRead }
                if (oldLastReadXor != newLastReadXor) return@distinctUntilChanged false

                val oldThumbSum = old.second.fold(0) { acc, l -> acc + (l.manga.thumbnailUrl?.hashCode() ?: 0) }
                val newThumbSum = new.second.fold(0) { acc, l -> acc + (l.manga.thumbnailUrl?.hashCode() ?: 0) }
                oldThumbSum == newThumbSum
            }
            .onEach {
                try {
                    UpdatesGridGlanceWidget().updateAll(this)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }
}
