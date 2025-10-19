package nl.stoux.tfw.core.common.database.entity

/**
 * Centralized artwork URL for an Edition.
 * Prefers the optimized WebP poster; falls back to the original poster when absent.
 */
val EditionEntity.artworkUrl: String?
    get() = posterOptimizedUrl ?: posterUrl
