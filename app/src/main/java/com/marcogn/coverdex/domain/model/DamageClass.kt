package com.marcogn.coverdex.domain.model

/**
 * A move's damage class. Ids match `moves.csv`'s `damage_class_id` foreign key into
 * `move_damage_classes` — verified as 1 = STATUS, 2 = PHYSICAL, 3 = SPECIAL (see
 * docs/plan/reference-pokedata.md §3).
 */
enum class DamageClass {
    STATUS,
    PHYSICAL,
    SPECIAL,
}
