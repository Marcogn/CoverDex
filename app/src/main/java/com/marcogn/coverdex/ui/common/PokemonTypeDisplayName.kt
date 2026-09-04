package com.marcogn.coverdex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.coverdex.R
import com.marcogn.coverdex.domain.model.PokemonType

/** The type's localized display name — official Italian/English names, verified against
 * Bulbapedia. Kept off [PokemonType] itself, which stays Android-free (`domain/` has no resource
 * access); [TypeBadge] and text-based type pickers both need it, first exercised end-to-end by
 * the slot editor's type-override dropdowns. */
@Composable
fun PokemonType.displayName(): String = stringResource(
    when (this) {
        PokemonType.NORMAL -> R.string.type_normal
        PokemonType.FIGHTING -> R.string.type_fighting
        PokemonType.FLYING -> R.string.type_flying
        PokemonType.POISON -> R.string.type_poison
        PokemonType.GROUND -> R.string.type_ground
        PokemonType.ROCK -> R.string.type_rock
        PokemonType.BUG -> R.string.type_bug
        PokemonType.GHOST -> R.string.type_ghost
        PokemonType.STEEL -> R.string.type_steel
        PokemonType.FIRE -> R.string.type_fire
        PokemonType.WATER -> R.string.type_water
        PokemonType.GRASS -> R.string.type_grass
        PokemonType.ELECTRIC -> R.string.type_electric
        PokemonType.PSYCHIC -> R.string.type_psychic
        PokemonType.ICE -> R.string.type_ice
        PokemonType.DRAGON -> R.string.type_dragon
        PokemonType.DARK -> R.string.type_dark
        PokemonType.FAIRY -> R.string.type_fairy
    },
)
