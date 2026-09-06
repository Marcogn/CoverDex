package com.marcogn.coverdex.data.pokeapi

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The `PokeAPI/pokeapi` commit this app's dataset is pinned to, so two syncs produce the same
 * catalogue even though upstream `master` moves. Resolved 2026-09-04 — see
 * docs/plan/reference-pokedata.md §6. Bumping it is a normal PR with a CHANGELOG bullet. */
const val DATASET_REVISION = "d4f9a4af58ade123fbc0558f68b1c69daa97d9e4"

private const val BASE_URL = "https://raw.githubusercontent.com/PokeAPI/pokeapi/$DATASET_REVISION/data/v2/csv"
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val RETRY_DELAY_MS = 500L

/** The 12 pinned CSV files that make up the whole dataset — see
 * docs/plan/reference-pokedata.md §2 for exact sizes and headers. The last four were added in
 * Phase 7 (docs/plan/phase-7-accuracy-and-customization.md §2) for base stats and correct
 * English ability/move names; they are read at the same pinned [DATASET_REVISION] as the
 * original eight. */
enum class DatasetFile(val fileName: String) {
    POKEMON("pokemon.csv"),
    SPECIES("pokemon_species.csv"),
    POKEMON_TYPES("pokemon_types.csv"),
    POKEMON_ABILITIES("pokemon_abilities.csv"),
    ABILITIES("abilities.csv"),
    MOVES("moves.csv"),
    TYPES("types.csv"),
    TYPE_EFFICACY("type_efficacy.csv"),
    POKEMON_STATS("pokemon_stats.csv"),
    POKEMON_STATS_PAST("pokemon_stats_past.csv"),
    ABILITY_NAMES("ability_names.csv"),
    MOVE_NAMES("move_names.csv"),
}

/** Source of the 8 pinned CSV files. The only reason this is an interface rather than just
 * [PokeDataClient] directly: [com.marcogn.coverdex.data.pokeapi.DatasetSyncManager]'s tests fake
 * it with canned CSV text instead of hitting the real network — no mocking library needed, and
 * none is in the pinned dependency catalogue. See `di/NetworkModule.kt`. */
interface DatasetSource {
    suspend fun fetchAll(): Map<DatasetFile, String>
}

/**
 * Hand-rolled client for PokéAPI's own CSV source data — no Retrofit/Ktor, same
 * `HttpURLConnection` pattern as Hall of Memories' `PokeApiClient`. No API key, no auth.
 */
@Singleton
class PokeDataClient @Inject constructor() : DatasetSource {

    /** Fetches every [DatasetFile] concurrently (there are only 8, no need for a concurrency
     * cap), one retry per file after [RETRY_DELAY_MS] on failure. */
    override suspend fun fetchAll(): Map<DatasetFile, String> = coroutineScope {
        DatasetFile.entries.map { file ->
            async { file to getCsv(file) }
        }.awaitAll().toMap()
    }

    private suspend fun getCsv(file: DatasetFile): String = withContext(Dispatchers.IO) {
        try {
            fetch(file.fileName)
        } catch (e: Exception) {
            delay(RETRY_DELAY_MS)
            fetch(file.fileName)
        }
    }

    private fun fetch(fileName: String): String {
        val connection = (URL("$BASE_URL/$fileName").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", USER_AGENT)
            // Deliberately no Accept-Encoding header: HttpURLConnection negotiates gzip and
            // decompresses transparently on its own. Setting it by hand hands back raw gzip
            // bytes instead — see CLAUDE.md, "Known gotchas". CSV compresses very well, so this
            // matters more here than anywhere else in the app.
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} @ $BASE_URL/$fileName" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
