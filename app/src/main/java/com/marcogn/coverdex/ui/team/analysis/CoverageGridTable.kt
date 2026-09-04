package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.ui.common.TypeBadge

private val NAME_COLUMN_WIDTH = 96.dp
private val TYPE_COLUMN_WIDTH = 40.dp
private val ROW_HEIGHT = 32.dp

/**
 * One member's grid row: its display name and a multiplier per defending/attacking [PokemonType]
 * (offensive or defensive, decided by the caller).
 */
data class GridRow(val member: TeamMember, val multiplierByType: Map<PokemonType, Double>)

/**
 * The shared renderer for both the offensive and defensive coverage grids —
 * `CoverageGrid.tsx`'s two nearly-identical `<table>`s, unified here into one reusable
 * composable. The name column is pinned; only the 18 type columns (plus the summary row) scroll
 * horizontally, and that scrolling never reaches the screen itself
 * (`phase-3-analysis.md` §2: "the screen itself must never scroll sideways").
 */
@Composable
fun CoverageGridTable(
    title: String,
    pokemonColumnHeader: String,
    rows: List<GridRow>,
    footerLabel: String,
    footerMultiplierByType: Map<PokemonType, Double>,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        Row {
            // Pinned name column.
            Column {
                GridCell(pokemonColumnHeader, width = NAME_COLUMN_WIDTH, bold = true)
                rows.forEach { row ->
                    GridCell(row.member.speciesName, width = NAME_COLUMN_WIDTH, ellipsis = true)
                }
                GridCell(footerLabel, width = NAME_COLUMN_WIDTH, bold = true, ellipsis = true)
            }
            // Horizontally scrolling type columns + summary row.
            Column(modifier = Modifier.horizontalScroll(hScroll)) {
                Row {
                    PokemonType.entries.forEach { type ->
                        Box(modifier = Modifier.width(TYPE_COLUMN_WIDTH).height(ROW_HEIGHT), contentAlignment = Alignment.Center) {
                            TypeBadge(type, size = 18.dp)
                        }
                    }
                }
                rows.forEach { row ->
                    Row {
                        PokemonType.entries.forEach { type ->
                            MultiplierCell(row.multiplierByType[type] ?: 1.0)
                        }
                    }
                }
                Row {
                    PokemonType.entries.forEach { type ->
                        MultiplierCell(footerMultiplierByType[type] ?: 1.0, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false, ellipsis: Boolean = false) {
    Box(modifier = Modifier.width(width).height(ROW_HEIGHT).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

@Composable
private fun MultiplierCell(mult: Double, bold: Boolean = false) {
    Box(
        modifier = Modifier
            .width(TYPE_COLUMN_WIDTH)
            .height(ROW_HEIGHT)
            .background(multiplierCellColor(mult)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            multiplierLabel(mult),
            style = MaterialTheme.typography.labelSmall,
            color = multiplierCellContentColor(mult),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
