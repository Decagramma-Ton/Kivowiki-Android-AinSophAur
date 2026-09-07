package wiki.kivo.core.data.local

import androidx.room.withTransaction
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import wiki.kivo.core.model.*

@Singleton
class LibraryRepository
@Inject
constructor(private val database: KivoDatabase, private val json: Json) {
    private val dao
        get() = database.dao()

    val bookmarks =
        dao.bookmarks().map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    SavedEntry(json.decodeFromString<ContentCard>(row.cardJson), row.savedAt)
                }
                    .getOrNull()
            }
        }
    val history =
        dao.history().map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    SavedEntry(
                        json.decodeFromString<ContentCard>(row.cardJson),
                        row.visitedAt,
                        row.position,
                        row.offset,
                    )
                }
                    .getOrNull()
            }
        }

    suspend fun addBookmark(card: ContentCard) =
        dao.bookmark(
            BookmarkRow(card.key.storageKey, json.encodeToString(card), Instant.now().epochSecond)
        )

    suspend fun removeBookmark(key: EntityKey) = dao.removeBookmark(key.storageKey)

    suspend fun progress(key: EntityKey): Pair<Int, Int> =
        dao.progress(key.storageKey)?.let { it.position to it.offset } ?: (0 to 0)

    suspend fun record(card: ContentCard, position: Int, offset: Int) = database.withTransaction {
        dao.visit(
            HistoryRow(
                card.key.storageKey,
                json.encodeToString(card),
                Instant.now().epochSecond,
                position.coerceAtLeast(0),
                offset.coerceAtLeast(0),
            )
        )
        dao.trimHistory()
    }

    suspend fun clearHistory() = dao.clearHistory()
}
