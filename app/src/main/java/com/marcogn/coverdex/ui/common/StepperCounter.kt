package com.marcogn.coverdex.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A labelled `−`/count/`+` row — Surprise Me's own constraint counters (0-floor, no ceiling
 * beyond the team's remaining budget) and Settings' suggestion-count stepper (5-10, Phase 7 —
 * see docs/plan/phase-7-accuracy-and-customization.md §6) share this one composable rather than
 * each keeping its own copy. [canDecrement]/[canIncrement] are the caller's own bounds check, not
 * assumed here — the two callers have different floors/ceilings.
 */
@Composable
fun StepperCounter(
    label: String,
    value: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = canDecrement) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onIncrement, enabled = canIncrement) { Text("+") }
    }
}
