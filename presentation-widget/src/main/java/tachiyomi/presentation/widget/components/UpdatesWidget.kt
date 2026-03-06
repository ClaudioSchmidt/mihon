package tachiyomi.presentation.widget.components

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.util.appWidgetInnerRadius
import tachiyomi.presentation.widget.util.calculateGridLayout

@Composable
fun UpdatesWidget(
    data: ImmutableList<Pair<Long, Bitmap?>>?,
    contentColor: ColorProvider,
    topPadding: Dp,
    bottomPadding: Dp,
    appWidgetId: Int,
    mergeLastTwoRows: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val grid = LocalSize.current.calculateGridLayout(topPadding, bottomPadding, mergeLastTwoRows)
    val context = LocalContext.current
    val normalSlots = grid.rowCount * grid.columnCount
    val totalSlots = normalSlots + (grid.mergedBottomRow?.columnCount ?: 0)
    // First empty slot index; -1 if data is null/empty (handled separately below)
    val settingsSlotIdx = if (data != null && data.isNotEmpty()) data.size else -1
    val hasEmptySlot = settingsSlotIdx in 0 until totalSlots

    val configIntent = Intent(
        context,
        Class.forName(Constants.WIDGET_CONFIG_ACTIVITY),
    ).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    Box(modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.fillMaxSize(),
        ) {
            if (data == null) {
                CircularProgressIndicator(color = contentColor)
            } else if (data.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.information_no_recent),
                    style = TextStyle(color = contentColor),
                )
            } else {
                // Gear visual slot counts from bottom-right: slot totalSlots-1 is bottom-right,
                // so the gear sits at totalSlots-1-data.size (the first empty slot from the right).
                val gearVisualSlot = if (hasEmptySlot) totalSlots - 1 - data.size else -1
                val settingsRow = if (gearVisualSlot in 0 until normalSlots) gearVisualSlot / grid.columnCount else -1
                val settingsCol = if (gearVisualSlot in 0 until normalSlots) gearVisualSlot % grid.columnCount else -1
                val settingsMergedCol = if (gearVisualSlot >= normalSlots) gearVisualSlot - normalSlots else -1

                Column(
                    modifier = GlanceModifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    (0..<grid.rowCount).forEach { i ->
                        val rowStart = i * grid.columnCount
                        val mangaInRow = (0..<grid.columnCount).count { j ->
                            data.getOrNull(totalSlots - 1 - (rowStart + j)) != null
                        }
                        val isSettingsRow = i == settingsRow

                        if (mangaInRow > 0 || isSettingsRow) {
                            Row(
                                modifier = GlanceModifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                (0..<grid.columnCount).forEach { j ->
                                    val item = data.getOrNull(totalSlots - 1 - (rowStart + j))
                                    when {
                                        item != null -> {
                                            val (mangaId, cover) = item
                                            Box(
                                                modifier = GlanceModifier
                                                    .padding(horizontal = grid.coverHorizontalPadding),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val intent = Intent(
                                                    context,
                                                    Class.forName(Constants.MAIN_ACTIVITY),
                                                ).apply {
                                                    action = Constants.SHORTCUT_MANGA
                                                    putExtra(Constants.MANGA_EXTRA, mangaId)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                    // https://issuetracker.google.com/issues/238793260
                                                    addCategory(mangaId.toString())
                                                }
                                                UpdatesMangaCover(
                                                    cover = cover,
                                                    coverWidth = grid.coverWidth,
                                                    coverHeight = grid.coverHeight,
                                                    modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                                                )
                                            }
                                        }
                                        j == settingsCol && isSettingsRow -> {
                                            // Settings tile in the first empty cover slot
                                            Box(
                                                modifier = GlanceModifier
                                                    .padding(horizontal = grid.coverHorizontalPadding),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    modifier = GlanceModifier
                                                        .size(width = grid.coverWidth, height = grid.coverHeight)
                                                        .appWidgetInnerRadius()
                                                        .background(ColorProvider(Color(0x25FFFFFF)))
                                                        .clickable(actionStartActivity(configIntent)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Image(
                                                        provider = ImageProvider(R.drawable.ic_widget_settings),
                                                        contentDescription = "Configure widget",
                                                        colorFilter = ColorFilter.tint(contentColor),
                                                        modifier = GlanceModifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Merged bottom row: spans 2 tile-row heights with larger covers
                    grid.mergedBottomRow?.let { mergedRow ->
                        val mergedRowStart = normalSlots
                        val mergedHasContent = (0..<mergedRow.columnCount).any { j ->
                            data.getOrNull(totalSlots - 1 - (normalSlots + j)) != null
                        }
                        if (mergedHasContent || settingsMergedCol >= 0) {
                            Row(
                                modifier = GlanceModifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                (0..<mergedRow.columnCount).forEach { j ->
                                    val item = data.getOrNull(totalSlots - 1 - (normalSlots + j))
                                    when {
                                        item != null -> {
                                            val (mangaId, cover) = item
                                            Box(
                                                modifier = GlanceModifier
                                                    .padding(horizontal = mergedRow.coverHorizontalPadding),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val intent = Intent(
                                                    context,
                                                    Class.forName(Constants.MAIN_ACTIVITY),
                                                ).apply {
                                                    action = Constants.SHORTCUT_MANGA
                                                    putExtra(Constants.MANGA_EXTRA, mangaId)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                    addCategory(mangaId.toString())
                                                }
                                                UpdatesMangaCover(
                                                    cover = cover,
                                                    coverWidth = mergedRow.coverWidth,
                                                    coverHeight = mergedRow.coverHeight,
                                                    modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                                                )
                                            }
                                        }
                                        j == settingsMergedCol -> {
                                            Box(
                                                modifier = GlanceModifier
                                                    .padding(horizontal = mergedRow.coverHorizontalPadding),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    modifier = GlanceModifier
                                                        .size(width = mergedRow.coverWidth, height = mergedRow.coverHeight)
                                                        .appWidgetInnerRadius()
                                                        .background(ColorProvider(Color(0x25FFFFFF)))
                                                        .clickable(actionStartActivity(configIntent)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Image(
                                                        provider = ImageProvider(R.drawable.ic_widget_settings),
                                                        contentDescription = "Configure widget",
                                                        colorFilter = ColorFilter.tint(contentColor),
                                                        modifier = GlanceModifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fallback: small gear at top-right only when every cover slot is filled
        if (data != null && data.isNotEmpty() && !hasEmptySlot) {
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = grid.coverHorizontalPadding, vertical = 2.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(14.dp)
                        .background(ColorProvider(Color(0x60000000)))
                        .clickable(actionStartActivity(configIntent)),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_settings),
                        contentDescription = "Configure widget",
                        colorFilter = ColorFilter.tint(contentColor),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
        }
    }
}
