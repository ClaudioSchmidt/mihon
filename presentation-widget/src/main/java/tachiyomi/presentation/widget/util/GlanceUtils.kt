package tachiyomi.presentation.widget.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import tachiyomi.presentation.widget.R

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_background_radius)
}

fun GlanceModifier.appWidgetInnerRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_inner_radius)
}

/**
 * Layout descriptor for the merged bottom row: when enabled, the last two tile-rows
 * are collapsed into one taller row of larger covers.
 */
data class MergedRowLayout(
    val columnCount: Int,
    val coverWidth: Dp,
    val coverHeight: Dp,
    val coverHorizontalPadding: Dp,
)

/**
 * Holds the computed grid layout for a widget: how many rows/columns,
 * the adaptive cover dimensions that fill the available space,
 * and the horizontal padding to distribute evenly around each cover.
 */
data class WidgetGridLayout(
    val rowCount: Int,
    val columnCount: Int,
    val coverWidth: Dp,
    val coverHeight: Dp,
    val coverHorizontalPadding: Dp,
    val mergedBottomRow: MergedRowLayout? = null,
)

// Approximate height of one home-screen tile in dp
private const val TARGET_ROW_HEIGHT = 80f
// Manga cover width/height ratio
private const val COVER_ASPECT_RATIO = 0.7f
// Vertical gap per row subtracted from the cover height (2dp top + 2dp bottom)
private const val VERTICAL_COVER_GAP = 4f
// Minimum horizontal gap between covers, used to determine how many fit
private const val MIN_COVER_GAP = 3f

/**
 * Calculates an adaptive grid layout for the widget.
 *
 * Row count is derived so that 1 row ≈ 1 home-screen tile height.
 * Cover height fills each row's allocated height (row height minus a small gap).
 * Cover width is derived from the manga cover aspect ratio (0.7).
 * Column count is how many covers of that width fit horizontally.
 * Remaining horizontal space is distributed evenly as padding around each cover.
 *
 * When [mergeLastTwoRows] is true and the widget is at least 2 tile-rows tall,
 * the bottom two tile-rows are merged into a single taller row of larger covers.
 */
fun DpSize.calculateGridLayout(
    topPadding: Dp,
    bottomPadding: Dp,
    mergeLastTwoRows: Boolean = false,
): WidgetGridLayout {
    val availableHeight = (this.height - topPadding - bottomPadding).value.coerceAtLeast(0f)
    val availableWidth = this.width.value.coerceAtLeast(0f)

    // Tile rows: 1 per ~80dp; use floor (toInt) to avoid HALF_UP rounding jumping to an extra row,
    // which would make covers smaller in a taller widget than in a shorter one.
    val tileRows = (availableHeight / TARGET_ROW_HEIGHT).toInt().coerceIn(1, 10)

    // Covers fill each row's allocated height, minus a small vertical gap
    val cellHeight = availableHeight / tileRows
    val coverHeightDp = (cellHeight - VERTICAL_COVER_GAP).coerceAtLeast(20f)

    // Width from aspect ratio
    val coverWidthDp = (coverHeightDp * COVER_ASPECT_RATIO).coerceAtLeast(14f)

    // Columns: how many covers fit horizontally (floor to prevent clipping)
    val columnCount = (availableWidth / (coverWidthDp + MIN_COVER_GAP)).toInt().coerceIn(1, 20)

    // Distribute remaining horizontal space evenly around each cover (SpaceAround)
    val remainingWidth = availableWidth - columnCount * coverWidthDp
    val coverHorizontalPadding = (remainingWidth / (2 * columnCount)).coerceAtLeast(MIN_COVER_GAP / 2f)

    // Merge: bottom two tile-rows collapse into one taller row of larger covers
    val canMerge = mergeLastTwoRows && tileRows >= 2
    val normalRows = if (canMerge) tileRows - 2 else tileRows

    val mergedBottomRow: MergedRowLayout? = if (canMerge) {
        val mergedCoverHeight = (2f * cellHeight - VERTICAL_COVER_GAP).coerceAtLeast(20f)
        val mergedCoverWidth = (mergedCoverHeight * COVER_ASPECT_RATIO).coerceAtLeast(14f)
        val mergedColumnCount = (availableWidth / (mergedCoverWidth + MIN_COVER_GAP)).toInt().coerceIn(1, 20)
        val mergedRemaining = availableWidth - mergedColumnCount * mergedCoverWidth
        val mergedHPad = (mergedRemaining / (2 * mergedColumnCount)).coerceAtLeast(MIN_COVER_GAP / 2f)
        MergedRowLayout(
            columnCount = mergedColumnCount,
            coverWidth = mergedCoverWidth.dp,
            coverHeight = mergedCoverHeight.dp,
            coverHorizontalPadding = mergedHPad.dp,
        )
    } else {
        null
    }

    return WidgetGridLayout(
        rowCount = normalRows,
        columnCount = columnCount,
        coverWidth = coverWidthDp.dp,
        coverHeight = coverHeightDp.dp,
        coverHorizontalPadding = coverHorizontalPadding.dp,
        mergedBottomRow = mergedBottomRow,
    )
}
