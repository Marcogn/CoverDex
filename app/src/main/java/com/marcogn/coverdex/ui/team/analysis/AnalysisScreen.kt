package com.marcogn.coverdex.ui.team.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.coverage.CoverageMode
import com.marcogn.coverdex.domain.coverage.defensiveMultiplier
import com.marcogn.coverdex.domain.coverage.mostVulnerableByType
import com.marcogn.coverdex.domain.coverage.offensiveMultipliersForMember
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.ui.common.EmptyState
import com.marcogn.coverdex.ui.common.TypeBadge

/**
 * `docs/plan/phase-3-analysis.md` §2 — seven sections, in this exact order. Sections 1-6 are the
 * real, ported coverage analysis; section 7 (Suggestions) is a placeholder until Phase 4.
 */
@Composable
fun AnalysisScreen(modifier: Modifier = Modifier, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (!state.canAnalyse) {
        EmptyState(
            title = stringResource(R.string.analysis_empty),
            subtitle = "",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val coverage = state.coverage
    val chart = state.chart
    if (coverage == null || chart == null) return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Section 1 — coverage basis notice.
        val basisNotice = when {
            coverage.mixed -> {
                val moveNames = state.members.filter { coverage.modePerMember[it.id] == CoverageMode.MOVES }.joinToString(", ") { it.speciesName }
                val typeNames = state.members.filter { coverage.modePerMember[it.id] == CoverageMode.TYPES }.joinToString(", ") { it.speciesName }
                stringResource(R.string.analysis_basis_mixed, moveNames, typeNames)
            }
            state.members.isNotEmpty() && coverage.modePerMember.values.all { it == CoverageMode.MOVES } -> stringResource(R.string.analysis_basis_moves_only)
            else -> stringResource(R.string.analysis_basis_types_only)
        }
        Text(
            basisNotice,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )

        // Section 2 — per-Pokémon breakdown.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.analysis_per_pokemon))
            state.members.forEach { member -> PerPokemonCard(member = member, chart = chart) }
        }

        // Section 3 — offensive grid.
        CoverageGridTable(
            title = stringResource(R.string.analysis_offensive_coverage),
            pokemonColumnHeader = stringResource(R.string.analysis_pokemon_column_header),
            rows = state.members.map { GridRow(it, offensiveMultipliersForMember(chart, it)) },
            footerLabel = stringResource(R.string.analysis_team_best),
            footerMultiplierByType = coverage.bestMultiplierByType,
        )

        // Section 4 — defensive grid.
        val mostVulnerable = mostVulnerableByType(chart, state.members)
        CoverageGridTable(
            title = stringResource(R.string.analysis_defensive_coverage),
            pokemonColumnHeader = stringResource(R.string.analysis_pokemon_column_header),
            rows = state.members.map { member ->
                val byAttackType = PokemonType.entries.associateWith { atk -> defensiveMultiplier(chart, atk, member.types, member.ability) }
                GridRow(member, byAttackType)
            },
            footerLabel = stringResource(R.string.analysis_most_vulnerable),
            footerMultiplierByType = mostVulnerable,
        )

        // Section 5 — shared weaknesses.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.analysis_shared_weaknesses))
            if (state.sharedWeaknesses.isEmpty()) {
                Text(stringResource(R.string.analysis_no_shared_weaknesses), style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sharedWeaknesses.forEach { (type, count) ->
                        Row {
                            TypeBadge(type)
                            Text("×$count", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section 6 — uncovered types.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.analysis_uncovered_types))
            if (coverage.uncovered.isEmpty()) {
                Text(stringResource(R.string.analysis_full_coverage), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    Text(stringResource(R.string.analysis_missing_coverage), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        coverage.uncovered.forEach { TypeBadge(it) }
                    }
                }
            }
        }

        // Section 7 — suggestions (Phase 4).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.analysis_suggestions))
            Text(
                stringResource(R.string.analysis_suggestions_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}
