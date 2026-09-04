package com.marcogn.coverdex.domain.backup

import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.Team
import com.marcogn.coverdex.domain.model.TeamMember
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The current format this build writes and the newest one it can read — see
 * [BackupFormatTooNewException]. */
const val CURRENT_BACKUP_FORMAT_VERSION = 1

/** Thrown when a backup's `formatVersion` is newer than this build understands — a clear, typed
 * rejection rather than a parse crash or silent data loss. */
class BackupFormatTooNewException(val fileVersion: Int) :
    Exception("Backup format $fileVersion is newer than this app supports (max $CURRENT_BACKUP_FORMAT_VERSION)")

/**
 * The local backup format (`docs/plan/phase-5-import-export-and-settings.md` §4): a single zip
 * with this as `data.json` — every team and the custom roster. **Never the Pokédex cache** — it
 * is re-downloadable, not user data. Enum fields on [BackupTeamMemberDto]/[BackupMoveDto] are
 * plain strings, not the enums themselves — copied from Hall of Memories' own `BackupPayload.kt`,
 * which makes the same choice for its own domain enums — using [PokemonType.apiName] and
 * [DamageClass.name] with the same fallback-on-corrupt-value philosophy
 * `data/repository/TeamMappers.kt`'s `parseType`/`parseDamageClass` already apply to this exact
 * data (team/roster rows are written exclusively by this app's own code, but a hand-edited or
 * corrupted backup file is still user input from this format's point of view).
 */
@Serializable
data class BackupPayload(
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    /** Epoch millis, not an ISO string — `minSdk` stays 24 (`docs/plan/native-spec.md`,
     * "Identity") and `java.time` needs API 26 without desugaring, the same reason
     * `Team.createdAtEpochMillis` and every other timestamp in this app is a plain `Long`. */
    val exportedAtEpochMillis: Long,
    val teams: List<BackupTeamDto> = emptyList(),
    val customPokemon: List<BackupTeamMemberDto> = emptyList(),
)

@Serializable
data class BackupMoveDto(
    val id: String,
    val name: String,
    val type: String,
    val power: Int?,
    val damageClass: String,
    val isCustom: Boolean,
)

@Serializable
data class BackupTeamMemberDto(
    val id: String,
    val pokedexId: Int?,
    val speciesName: String,
    val type1: String,
    val type2: String?,
    val ability: String?,
    /** Always length 4, `null` for an empty move slot. */
    val moves: List<BackupMoveDto?>,
    val isCustomSaved: Boolean,
)

@Serializable
data class BackupTeamDto(
    val id: String,
    val name: String,
    /** Always length 6, `null` for an empty slot. */
    val members: List<BackupTeamMemberDto?>,
    val createdAtEpochMillis: Long,
)

private fun parseBackupType(apiName: String): PokemonType = PokemonType.fromApiName(apiName) ?: PokemonType.NORMAL

private fun parseBackupDamageClass(name: String): DamageClass = runCatching { DamageClass.valueOf(name) }.getOrElse { DamageClass.PHYSICAL }

fun PokemonMove.toBackupDto(): BackupMoveDto = BackupMoveDto(
    id = id, name = name, type = type.apiName, power = power, damageClass = damageClass.name, isCustom = isCustom,
)

fun BackupMoveDto.toDomain(): PokemonMove = PokemonMove(
    id = id, name = name, type = parseBackupType(type), power = power, damageClass = parseBackupDamageClass(damageClass), isCustom = isCustom,
)

fun TeamMember.toBackupDto(): BackupTeamMemberDto = BackupTeamMemberDto(
    id = id,
    pokedexId = pokedexId,
    speciesName = speciesName,
    type1 = types.first.apiName,
    type2 = types.second?.apiName,
    ability = ability,
    moves = moves.map { it?.toBackupDto() },
    isCustomSaved = isCustomSaved,
)

fun BackupTeamMemberDto.toDomain(): TeamMember = TeamMember(
    id = id,
    pokedexId = pokedexId,
    speciesName = speciesName,
    types = parseBackupType(type1) to type2?.let { parseBackupType(it) },
    ability = ability,
    moves = moves.map { it?.toDomain() },
    isCustomSaved = isCustomSaved,
)

fun Team.toBackupDto(): BackupTeamDto = BackupTeamDto(
    id = id,
    name = name,
    members = members.map { it?.toBackupDto() },
    createdAtEpochMillis = createdAtEpochMillis,
)

fun BackupTeamDto.toDomain(): Team = Team(
    id = id,
    name = name,
    members = members.map { it?.toDomain() },
    createdAtEpochMillis = createdAtEpochMillis,
)

private val backupJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun BackupPayload.toJson(): String = backupJson.encodeToString(this)

/** @throws BackupFormatTooNewException if the decoded payload's `formatVersion` is newer than
 * this build understands. */
fun String.toBackupPayload(): BackupPayload {
    val payload = backupJson.decodeFromString<BackupPayload>(this)
    if (payload.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) throw BackupFormatTooNewException(payload.formatVersion)
    return payload
}
