package com.marcogn.coverdex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.DamageClass

/** The damage category's localized display name — "Fisica"/"Speciale"/"Stato", the feminine
 * adjective forms agreeing with "mossa" (Italian "move"), matching Bulbapedia's own "Mossa
 * speciale" phrasing. */
@Composable
fun DamageClass.displayName(): String = stringResource(
    when (this) {
        DamageClass.PHYSICAL -> R.string.damage_class_physical
        DamageClass.SPECIAL -> R.string.damage_class_special
        DamageClass.STATUS -> R.string.damage_class_status
    },
)
