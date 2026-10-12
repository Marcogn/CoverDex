package com.marcogn.coverdex.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The fields of a GitHub `releases/latest` response the update flow uses; everything else is ignored. */
data class ReleaseInfo(
    val tagName: String,
    val name: String?,
    /** Markdown as published by `release.yml`: the CHANGELOG bold headlines + a "See CHANGELOG.md" line. */
    val body: String?,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** `"sha256:<hex>"` when GitHub provides it; documented as nullable (older assets have none). */
    val digest: String?,
)

// coerceInputValues + nullable fields: GitHub sends explicit `null` for `body`/`name`/`digest`, and a
// default value only covers a missing key (see CLAUDE.md, "Known gotchas").
private val releaseJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto>? = null,
)

@Serializable
private data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
    val digest: String? = null,
)

/**
 * Parses a `GET /repos/{owner}/{repo}/releases/latest` body. Throws (a `SerializationException`,
 * i.e. an `IllegalArgumentException`) when a required field is missing or malformed: the caller turns
 * that into "no update offered", never a guess.
 */
fun parseLatestRelease(json: String): ReleaseInfo {
    val dto = releaseJson.decodeFromString(ReleaseDto.serializer(), json)
    return ReleaseInfo(
        tagName = dto.tagName,
        name = dto.name,
        body = dto.body,
        htmlUrl = dto.htmlUrl,
        isDraft = dto.draft,
        isPrerelease = dto.prerelease,
        assets = dto.assets.orEmpty().map { asset ->
            ReleaseAsset(
                name = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                sizeBytes = asset.size,
                digest = asset.digest,
            )
        },
    )
}

/** The APK asset choice: exactly one asset whose name ends in `.apk`. */
sealed interface ApkAssetPick {
    data class Found(val asset: ReleaseAsset) : ApkAssetPick
    data object None : ApkAssetPick
    data object Ambiguous : ApkAssetPick
}

/**
 * Picks by extension, not by a fixed file name: the kit's release workflow has renamed assets before
 * in sibling apps, and an R8 `…-mapping.txt` may sit next to the APK. Several `.apk` files is
 * ambiguous (which one is for this device?) and so is no update, same as none.
 */
fun pickApkAsset(assets: List<ReleaseAsset>): ApkAssetPick {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    return when (apks.size) {
        0 -> ApkAssetPick.None
        1 -> ApkAssetPick.Found(apks.single())
        else -> ApkAssetPick.Ambiguous
    }
}

private val SHA256_DIGEST = Regex("""^sha256:([0-9a-fA-F]{64})$""")

/**
 * The lowercase hex SHA-256 from an asset `digest`, or `null` when there is none or it uses another
 * algorithm — then only the size and the signature check (which Android repeats anyway) apply.
 */
fun sha256FromDigest(digest: String?): String? =
    digest?.trim()?.let { SHA256_DIGEST.matchEntire(it) }?.groupValues?.get(1)?.lowercase()
