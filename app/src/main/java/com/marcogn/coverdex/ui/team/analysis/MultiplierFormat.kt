package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** `CoverageGrid.tsx`'s `multLabel` — the type chart only ever produces {0, 0.25, 0.5, 1, 2, 4},
 * all exactly representable in binary floating point, so the direct equality checks are safe. */
fun multiplierLabel(mult: Double): String = when (mult) {
    0.0 -> "0×"
    0.25 -> "¼×"
    0.5 -> "½×"
    1.0 -> "1×"
    2.0 -> "2×"
    4.0 -> "4×"
    else -> "${mult}×"
}

/** `CoverageGrid.tsx`'s `cellClass` — semantic, not brand, colour: immune/super-effective/
 * resisted/neutral. Kept as fixed colours (not [MaterialTheme] tokens) since they encode a
 * meaning independent of the app's own light/dark palette, same as the web version's fixed
 * red/emerald/orange/grey classes. */
@Composable
fun multiplierCellColor(mult: Double): Color = when {
    mult == 0.0 -> Color(0xFFB91C1C)
    mult >= 2.0 -> Color(0xFF059669)
    mult in 0.0..1.0 && mult < 1.0 -> Color(0xFFC2410C)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun multiplierCellContentColor(mult: Double): Color =
    if (mult == 1.0) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
