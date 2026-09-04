package com.marcogn.coverdex.domain.model

/**
 * A snapshot of the cached catalogue's state, exposed by
 * [com.marcogn.coverdex.domain.repository.PokedexRepository.cacheStatus]. [isUsable] is false
 * when there is no meta row yet, or its schema version or dataset revision doesn't match the
 * app's current ones — any of those means "treat the cache as absent", never a crash.
 */
data class CacheStatus(
    val isUsable: Boolean,
    val syncedAtEpochMillis: Long?,
    val speciesCount: Int,
    val moveCount: Int,
    val datasetRevision: String?,
)
