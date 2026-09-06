package com.marcogn.coverdex.domain.showdown

import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.MoveEntry
import com.marcogn.coverdex.domain.model.PokemonEntry
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.TeamMember
import java.util.UUID

/**
 * A direct port of `legacy-web/src/utils/showdownParser.ts` — same function names, same contract
 * (`docs/plan/native-spec.md`, "Showdown format contract"). **External users may rely on
 * round-tripping**, so this is a contract, not an implementation: change the written/read shape
 * only with a deliberate, documented reason.
 *
 * Both resolver parameters are plain, synchronous lookups rather than `PokedexRepository` calls
 * directly, keeping this file Android-free and unit-testable on the plain JVM — the caller
 * resolves them ahead of time (e.g. from an already-loaded `allSpecies()`/a single `moveByName`
 * call) and passes in a closure. [resolveSpecies] returns the full [PokemonEntry], not just its
 * types like the TypeScript's `resolveTypes`: this app resolves a sprite from
 * [TeamMember.pokedexId], never a stored URL, so the id has to come back from resolution too, not
 * only the types.
 */

private fun makeUnknownMove(name: String): PokemonMove = PokemonMove(
    id = UUID.randomUUID().toString(),
    name = name,
    type = PokemonType.NORMAL,
    power = null,
    damageClass = DamageClass.STATUS,
    isCustom = true,
)

private fun MoveEntry.toPokemonMove(): PokemonMove = PokemonMove(
    id = UUID.randomUUID().toString(),
    name = displayName,
    type = type,
    power = power,
    damageClass = damageClass,
    isCustom = false,
)

/** Convert a [TeamMember] to a Showdown-style block. [TeamMember.item] round-trips as the
 * standard `Species @ Item` line (Phase 7 — see
 * docs/plan/phase-7-accuracy-and-customization.md §4.3); EVs and nature are still untracked and
 * emitted as placeholders that are valid to re-import. */
fun exportMemberToShowdown(m: TeamMember): String {
    val lines = mutableListOf<String>()
    lines += "${m.speciesName} @ ${m.item ?: ""}"
    lines += "Ability: ${m.ability ?: ""}"
    lines += "EVs: "
    lines += " Nature"
    m.moves.forEach { mv -> if (mv != null) lines += "- ${mv.name}" }
    // Include the typing as a comment so a round-trip preserves type overrides.
    val typesStr = listOfNotNull(m.types.first, m.types.second).joinToString("/") { it.apiName }
    lines += "# Types: $typesStr"
    return lines.joinToString("\n")
}

fun exportTeamToShowdown(members: List<TeamMember?>): String =
    members.filterNotNull().joinToString("\n\n") { exportMemberToShowdown(it) }

data class ImportedMember(
    val member: TeamMember,
    /** Move names the user must complete (type/power) — resolution failed, so a placeholder
     * stands in and the block still imports. */
    val unknownMoveNames: List<String>,
    /** `false` means the caller should skip this block. */
    val speciesKnown: Boolean,
    /** The raw species name as parsed, regardless of [speciesKnown]. */
    val speciesName: String,
)

private val EVS_IVS_NATURE_REGEX = Regex("EVs:|IVs:|Nature", RegexOption.IGNORE_CASE)
private val ABILITY_LINE_REGEX = Regex("^Ability:\\s*", RegexOption.IGNORE_CASE)

/** Parse a single Showdown block into a [TeamMember]. */
fun parseShowdownBlock(
    block: String,
    resolveMove: (String) -> MoveEntry?,
    resolveSpecies: (String) -> PokemonEntry?,
): ImportedMember {
    val lines = block.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
    var speciesName = "Unknown"
    var overrideTypes: Pair<PokemonType, PokemonType?>? = null
    var ability: String? = null
    var item: String? = null
    val moves = arrayOfNulls<PokemonMove>(4)
    var moveIdx = 0
    val unknown = mutableListOf<String>()

    for (line in lines) {
        when {
            line.startsWith("- ") -> {
                val moveName = line.substring(2).trim()
                val known = resolveMove(moveName)
                val mv = if (known != null) {
                    known.toPokemonMove()
                } else {
                    unknown += moveName
                    makeUnknownMove(moveName)
                }
                if (moveIdx < 4) moves[moveIdx++] = mv
            }
            line.startsWith("ability:", ignoreCase = true) -> {
                val value = line.replaceFirst(ABILITY_LINE_REGEX, "").trim()
                if (value.isNotEmpty()) ability = value
            }
            EVS_IVS_NATURE_REGEX.containsMatchIn(line) -> Unit // ignored
            line.startsWith("# Types:") -> {
                val parts = line.removePrefix("# Types:").trim().split("/").map { it.trim().lowercase() }
                val t1 = parts.getOrNull(0)?.let { PokemonType.fromApiName(it) }
                if (t1 != null) {
                    val t2 = parts.getOrNull(1)?.let { PokemonType.fromApiName(it) }
                    overrideTypes = t1 to t2
                }
            }
            !line.startsWith("#") -> {
                // Species line, possibly with "@ item".
                val speciesLine = line.substringBefore("@").trim()
                if (speciesLine.isNotEmpty()) speciesName = speciesLine
                if (line.contains("@")) {
                    val itemValue = line.substringAfter("@").trim()
                    if (itemValue.isNotEmpty()) item = itemValue
                }
            }
        }
    }

    val resolved = resolveSpecies(speciesName)
    val types = overrideTypes ?: resolved?.types ?: (PokemonType.NORMAL to null)

    val member = TeamMember(
        id = UUID.randomUUID().toString(),
        pokedexId = resolved?.id,
        speciesName = speciesName,
        types = types,
        ability = ability,
        item = item,
        moves = moves.toList(),
        isCustomSaved = false,
    )
    return ImportedMember(
        member = member,
        unknownMoveNames = unknown,
        speciesKnown = resolved != null,
        speciesName = speciesName,
    )
}

fun parseShowdownTeam(
    text: String,
    resolveMove: (String) -> MoveEntry?,
    resolveSpecies: (String) -> PokemonEntry?,
): List<ImportedMember> =
    text.split(Regex("\n\\s*\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { parseShowdownBlock(it, resolveMove, resolveSpecies) }

/** A block whose species couldn't be resolved against the catalogue. */
data class ImportError(val speciesName: String)

data class ImportResult(val members: List<ImportedMember>, val errors: List<ImportError>)

/**
 * Higher-level import that drops blocks whose species cannot be resolved and surfaces them as
 * [ImportResult.errors]. The caller should show an error and leave the team slots untouched for
 * the dropped entries.
 */
fun importShowdownTeam(
    text: String,
    resolveMove: (String) -> MoveEntry?,
    resolveSpecies: (String) -> PokemonEntry?,
): ImportResult {
    val blocks = parseShowdownTeam(text, resolveMove, resolveSpecies)
    val members = mutableListOf<ImportedMember>()
    val errors = mutableListOf<ImportError>()
    for (b in blocks) {
        if (b.speciesKnown) members += b else errors += ImportError(b.speciesName)
    }
    return ImportResult(members, errors)
}
