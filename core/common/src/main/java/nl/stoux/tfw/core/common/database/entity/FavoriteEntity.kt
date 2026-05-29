package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of the user's favorited livesets. Mirrors `/api/favorites`
 * so the heart icon and favorites list show up offline too.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val livesetId: Long,
    val favoritedAt: String? = null,
)
