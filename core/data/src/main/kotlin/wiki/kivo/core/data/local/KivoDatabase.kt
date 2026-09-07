package wiki.kivo.core.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "public_cache")
data class CachedResponse(
    @PrimaryKey val queryKey: String,
    val payload: String,
    val fetchedAt: Long,
    val serverTime: Long?,
    val apiVersion: String,
)

/** 收藏和历史与可逐出的公共缓存分表，清理缓存永远不删除用户整理的记录。 */
@Entity(tableName = "bookmarks")
data class BookmarkRow(@PrimaryKey val entityKey: String, val cardJson: String, val savedAt: Long)

@Entity(tableName = "reading_history")
data class HistoryRow(
    @PrimaryKey val entityKey: String,
    val cardJson: String,
    val visitedAt: Long,
    val position: Int = 0,
    val offset: Int = 0,
)

@Dao
interface KivoDao {
    @Query("SELECT * FROM public_cache WHERE queryKey = :key")
    suspend fun cached(key: String): CachedResponse?

    @Upsert suspend fun cache(row: CachedResponse)

    @Query("DELETE FROM public_cache WHERE queryKey = :key") suspend fun invalidate(key: String)

    @Query("DELETE FROM public_cache") suspend fun clearCache()

    @Query("SELECT COALESCE(SUM(length(CAST(payload AS BLOB))),0) FROM public_cache")
    suspend fun cacheBytes(): Long

    @Query(
        "DELETE FROM public_cache WHERE queryKey IN (SELECT queryKey FROM public_cache ORDER BY fetchedAt LIMIT 8)"
    )
    suspend fun evictOldest()

    @Query("SELECT * FROM bookmarks ORDER BY savedAt DESC") fun bookmarks(): Flow<List<BookmarkRow>>

    @Upsert suspend fun bookmark(row: BookmarkRow)

    @Query("DELETE FROM bookmarks WHERE entityKey = :key") suspend fun removeBookmark(key: String)

    @Query("SELECT * FROM reading_history ORDER BY visitedAt DESC LIMIT 100")
    fun history(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM reading_history WHERE entityKey = :key")
    suspend fun progress(key: String): HistoryRow?

    @Upsert suspend fun visit(row: HistoryRow)

    @Query(
        "DELETE FROM reading_history WHERE entityKey NOT IN (SELECT entityKey FROM reading_history ORDER BY visitedAt DESC LIMIT 100)"
    )
    suspend fun trimHistory()

    @Query("DELETE FROM reading_history") suspend fun clearHistory()
}

@Database(
    entities = [CachedResponse::class, BookmarkRow::class, HistoryRow::class],
    version = 1,
    exportSchema = true,
)
abstract class KivoDatabase : RoomDatabase() {
    abstract fun dao(): KivoDao
}
