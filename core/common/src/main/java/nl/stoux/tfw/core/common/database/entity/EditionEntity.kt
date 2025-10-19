package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Edition table mirrors the Edition DTO for local storage.
 * Minimal fields needed for MVP browsing and display.
 */
@Entity(tableName = "editions")
data class EditionEntity(
    @PrimaryKey val id: Long,
    val number: String,
    val tagLine: String? = null,
    val date: String? = null,
    val notes: String? = null,
    val emptyNote: String? = null,
    val timetablerMode: Boolean = false,
    val posterUrl: String? = null,
    val posterOptimizedUrl: String? = null,
)
