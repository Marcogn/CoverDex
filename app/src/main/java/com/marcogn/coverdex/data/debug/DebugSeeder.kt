package com.marcogn.coverdex.data.debug

import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import com.marcogn.coverdex.domain.repository.CustomPokemonRepository
import com.marcogn.coverdex.domain.repository.TeamRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Seeds sample data behind `BuildConfig.SEED_DEBUG_DATA` (debug builds only, wired in
 * `CoverDexApplication.onCreate`) — never in a release build, never surfaced in the UI as real
 * user data. Two teams (one partial, one full six) plus two custom roster entries, so Phases 3
 * and 4's coverage/suggestion engines have something to analyse without hand-entering a team
 * every launch. A no-op once any team already exists, so it never re-seeds over real user data or
 * duplicates itself on every app start.
 */
@Singleton
class DebugSeeder @Inject constructor(
    private val teamRepository: TeamRepository,
    private val customPokemonRepository: CustomPokemonRepository,
) {
    suspend fun seed() {
        if (teamRepository.teams.first().isNotEmpty()) return

        val partialTeamId = teamRepository.createTeam("Kanto Starters")
        teamRepository.saveMember(partialTeamId, 0, bulbasaur())
        teamRepository.saveMember(partialTeamId, 1, charmander())
        teamRepository.saveMember(partialTeamId, 2, squirtle())

        val fullTeamId = teamRepository.createTeam("National Dex All-Stars")
        listOf(pikachu(), eevee(), snorlax(), gengar(), dragonite(), mewtwo())
            .forEachIndexed { index, member -> teamRepository.saveMember(fullTeamId, index, member) }

        customPokemonRepository.save(customFakemon())
        customPokemonRepository.save(customShinyVariant())
    }

    private fun move(name: String, type: PokemonType, power: Int?, damageClass: DamageClass) = PokemonMove(
        id = UUID.randomUUID().toString(),
        name = name,
        type = type,
        power = power,
        damageClass = damageClass,
        isCustom = false,
    )

    private fun member(
        pokedexId: Int,
        speciesName: String,
        type1: PokemonType,
        type2: PokemonType?,
        ability: String?,
        moves: List<PokemonMove?> = List(4) { null },
    ) = TeamMember(
        id = UUID.randomUUID().toString(),
        pokedexId = pokedexId,
        speciesName = speciesName,
        types = type1 to type2,
        ability = ability,
        moves = moves,
        isCustomSaved = false,
    )

    private fun bulbasaur() = member(
        1, "Bulbasaur", PokemonType.GRASS, PokemonType.POISON, "overgrow",
        listOf(
            move("Tackle", PokemonType.NORMAL, 40, DamageClass.PHYSICAL),
            move("Vine Whip", PokemonType.GRASS, 45, DamageClass.PHYSICAL),
            null,
            null,
        ),
    )

    private fun charmander() = member(
        4, "Charmander", PokemonType.FIRE, null, "blaze",
        listOf(
            move("Scratch", PokemonType.NORMAL, 40, DamageClass.PHYSICAL),
            move("Ember", PokemonType.FIRE, 40, DamageClass.SPECIAL),
            null,
            null,
        ),
    )

    private fun squirtle() = member(7, "Squirtle", PokemonType.WATER, null, "torrent")

    private fun pikachu() = member(
        25, "Pikachu", PokemonType.ELECTRIC, null, "static",
        listOf(move("Thunderbolt", PokemonType.ELECTRIC, 90, DamageClass.SPECIAL), null, null, null),
    )

    private fun eevee() = member(133, "Eevee", PokemonType.NORMAL, null, "run-away")

    private fun snorlax() = member(
        143, "Snorlax", PokemonType.NORMAL, null, "thick-fat",
        listOf(move("Body Slam", PokemonType.NORMAL, 85, DamageClass.PHYSICAL), null, null, null),
    )

    private fun gengar() = member(94, "Gengar", PokemonType.GHOST, PokemonType.POISON, "levitate")

    private fun dragonite() = member(149, "Dragonite", PokemonType.DRAGON, PokemonType.FLYING, "inner-focus")

    private fun mewtwo() = member(
        150, "Mewtwo", PokemonType.PSYCHIC, null, "pressure",
        listOf(move("Psychic", PokemonType.PSYCHIC, 90, DamageClass.SPECIAL), null, null, null),
    )

    private fun customFakemon() = TeamMember(
        id = UUID.randomUUID().toString(),
        pokedexId = null,
        speciesName = "Rockmander",
        types = PokemonType.ROCK to PokemonType.FIRE,
        ability = "sturdy",
        moves = List(4) { null },
        isCustomSaved = true,
    )

    private fun customShinyVariant() = TeamMember(
        id = UUID.randomUUID().toString(),
        pokedexId = null,
        speciesName = "Frostail",
        types = PokemonType.ICE to null,
        ability = "snow-cloak",
        moves = List(4) { null },
        isCustomSaved = true,
    )
}
