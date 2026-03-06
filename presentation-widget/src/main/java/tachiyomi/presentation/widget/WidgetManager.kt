package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
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
            .distinctUntilChanged { old, new ->
                old.third == new.third &&
                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet() &&
                    old.second.map { it.id }.toSet() == new.second.map { it.id }.toSet() &&
                    old.second.map { it.lastRead }.hashCode() == new.second.map { it.lastRead }.hashCode() &&
                    old.second.map { it.manga.thumbnailUrl }.hashCode() ==
                    new.second.map { it.manga.thumbnailUrl }.hashCode()
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
