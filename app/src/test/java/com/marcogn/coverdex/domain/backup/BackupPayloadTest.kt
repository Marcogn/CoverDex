package com.marcogn.coverdex.domain.backup

import com.marcogn.coverdex.domain.buildMember
import com.marcogn.coverdex.domain.model.DamageClass
import com.marcogn.coverdex.domain.model.PokemonMove
import com.marcogn.coverdex.domain.model.PokemonType
import com.marcogn.coverdex.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `docs/plan/phase-5-import-export-and-settings.md` §"Tests": DTO round-trip including empty
 * slots and null abilities, plus the JSON encode/decode path and the too-new-format rejection.
 */
class BackupPayloadTest {

    @Test
    fun `team member round-trips through the backup DTO with full fields`() {
        val original = buildMember(
            speciesName = "Charizard",
            types = PokemonType.FIRE to PokemonType.FLYING,
            moveTypes = listOf(PokemonType.FIRE, PokemonType.FLYING),
            ability = "Blaze",
        ).copy(pokedexId = 6, isCustomSaved = true)

        val roundTripped = original.toBackupDto().toDomain()

        assertEquals(original, roundTripped)
    }

    @Test
    fun `team member with a null ability and no moves round-trips`() {
        val original = buildMember("Snorlax", PokemonType.NORMAL to null)

        val roundTripped = original.toBackupDto().toDomain()

        assertEquals(original, roundTripped)
        assertEquals(null, roundTripped.ability)
        assertEquals(listOf(null, null, null, null), roundTripped.moves)
    }

    @Test
    fun `a single-type member has a null second type after round-trip`() {
        val original = buildMember("Snorlax", PokemonType.NORMAL to null)

        val roundTripped = original.toBackupDto().toDomain()

        assertEquals(PokemonType.NORMAL, roundTripped.types.first)
        assertEquals(null, roundTripped.types.second)
    }

    @Test
    fun `a team with empty slots round-trips, nulls preserved by position`() {
        val member = buildMember("Pikachu", PokemonType.ELECTRIC to null)
        val original = Team(
            id = "team-1",
            name = "Kanto Starters",
            members = listOf(member, null, null, null, null, null),
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        val roundTripped = original.toBackupDto().toDomain()

        assertEquals(original, roundTripped)
        assertEquals(6, roundTripped.members.size)
        assertEquals(null, roundTripped.members[1])
    }

    @Test
    fun `payload round-trips through JSON`() {
        val member = buildMember("Pikachu", PokemonType.ELECTRIC to null, ability = "Static")
        val team = Team(
            id = "team-1",
            name = "Kanto Starters",
            members = listOf(member, null, null, null, null, null),
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val payload = BackupPayload(
            exportedAtEpochMillis = 1_700_000_100_000L,
            teams = listOf(team.toBackupDto()),
            customPokemon = listOf(buildMember("Custom Mon", PokemonType.DRAGON to PokemonType.STEEL).toBackupDto()),
        )

        val decoded = payload.toJson().toBackupPayload()

        assertEquals(payload, decoded)
    }

    @Test
    fun `a payload from a newer format version is rejected`() {
        val payload = BackupPayload(formatVersion = CURRENT_BACKUP_FORMAT_VERSION + 1, exportedAtEpochMillis = 0L)
        val json = payload.toJson()

        assertThrows(BackupFormatTooNewException::class.java) { json.toBackupPayload() }
    }

    @Test
    fun `item round-trips through the backup DTO`() {
        val original = buildMember("Landorus", PokemonType.GROUND to PokemonType.FLYING).copy(item = "Air Balloon")

        val roundTripped = original.toBackupDto().toDomain()

        assertEquals("Air Balloon", roundTripped.item)
        assertEquals(original, roundTripped)
    }

    @Test
    fun `a v1 backup file (no item key at all) still decodes, item defaulting to null`() {
        // Hand-written, not produced via toJson() — a v1 file genuinely never had this key,
        // which is a different case from an explicit "item": null (CLAUDE.md's kotlinx.serialization
        // gotcha: a default only fills a MISSING key, not an explicit null).
        val v1Json = """
            {
              "formatVersion": 1,
              "exportedAtEpochMillis": 1700000100000,
              "teams": [],
              "customPokemon": [
                {
                  "id": "c1",
                  "pokedexId": null,
                  "speciesName": "Custom Mon",
                  "type1": "dragon",
                  "type2": "steel",
                  "ability": null,
                  "moves": [null, null, null, null],
                  "isCustomSaved": true
                }
              ]
            }
        """.trimIndent()

        val decoded = v1Json.toBackupPayload()

        assertEquals(1, decoded.formatVersion)
        assertEquals(null, decoded.customPokemon.single().item)
    }

    @Test
    fun `an unresolvable move type falls back to Normal instead of crashing`() {
        val move = PokemonMove(id = "m1", name = "Weird Move", type = PokemonType.FIRE, power = 40, damageClass = DamageClass.PHYSICAL, isCustom = false)
        val corrupted = move.toBackupDto().copy(type = "not-a-real-type")

        val recovered = corrupted.toDomain()

        assertEquals(PokemonType.NORMAL, recovered.type)
    }
}
