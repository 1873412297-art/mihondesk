@file:OptIn(ExperimentalSerializationApi::class)

package mihon.sync.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.Locale

@Serializable
data class MangaKey(
    val sourceId: Long,
    val url: String,
) {
    fun toKeyString(): String = "$sourceId:$url"

    companion object {
        fun parse(key: String): MangaKey {
            val idx = key.indexOf(':')
            require(idx != -1) { "Invalid MangaKey string: $key" }
            val sourceId = key.substring(0, idx).toLong()
            val url = key.substring(idx + 1)
            return MangaKey(sourceId, url)
        }
    }
}

@Serializable
data class ChapterKey(
    val mangaKey: MangaKey,
    val url: String,
) {
    fun toKeyString(): String = "${mangaKey.toKeyString()}::$url"

    companion object {
        fun parse(key: String): ChapterKey {
            val idx = key.lastIndexOf("::")
            require(idx != -1) { "Invalid ChapterKey string: $key" }
            val mangaKey = MangaKey.parse(key.substring(0, idx))
            val url = key.substring(idx + 2)
            return ChapterKey(mangaKey, url)
        }
    }
}

@Serializable
data class CategoryKey(
    val name: String,
) {
    val normalizedName: String = normalize(name)

    fun toKeyString(): String = normalizedName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CategoryKey) return false
        return normalizedName == other.normalizedName
    }

    override fun hashCode(): Int = normalizedName.hashCode()

    companion object {
        fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
        fun parse(key: String): CategoryKey = CategoryKey(normalize(key))
    }
}

@Serializable
data class HistoryKey(
    val chapterKey: ChapterKey,
) {
    fun toKeyString(): String = chapterKey.toKeyString()

    companion object {
        fun parse(key: String): HistoryKey = HistoryKey(ChapterKey.parse(key))
    }
}

@Serializable
data class TrackingKey(
    val mangaKey: MangaKey,
    val trackerId: Long,
) {
    fun toKeyString(): String = "${mangaKey.toKeyString()}#$trackerId"

    companion object {
        fun parse(key: String): TrackingKey {
            val idx = key.lastIndexOf('#')
            require(idx != -1) { "Invalid TrackingKey string: $key" }
            val mangaKey = MangaKey.parse(key.substring(0, idx))
            val trackerId = key.substring(idx + 1).toLong()
            return TrackingKey(mangaKey, trackerId)
        }
    }
}

val AndroidBackupManga.mangaKey: MangaKey get() = MangaKey(source, url)
fun AndroidBackupChapter.chapterKey(mangaKey: MangaKey): ChapterKey = ChapterKey(mangaKey, url)
val AndroidBackupCategory.categoryKey: CategoryKey get() = CategoryKey(name)
fun AndroidBackupHistory.historyKey(chapterKey: ChapterKey): HistoryKey = HistoryKey(chapterKey)
fun AndroidBackupTracking.trackingKey(mangaKey: MangaKey): TrackingKey = TrackingKey(mangaKey, syncId.toLong())

@Serializable
enum class EntityType {
    MANGA,
    CHAPTER,
    CATEGORY,
    HISTORY,
    TRACKING,
}

@Serializable
data class Tombstone(
    @ProtoNumber(1) val entityType: EntityType,
    @ProtoNumber(2) val entityKey: String,
    @ProtoNumber(3) val deletedAt: Long,
)

@Serializable
data class EntityDelta(
    @ProtoNumber(1) val mangas: List<AndroidBackupManga> = emptyList(),
    @ProtoNumber(2) val categories: List<AndroidBackupCategory> = emptyList(),
    @ProtoNumber(3) val sources: List<AndroidBackupSource> = emptyList(),
    @ProtoNumber(4) val preferences: List<AndroidBackupPreference> = emptyList(),
    @ProtoNumber(5) val sourcePreferences: List<AndroidBackupSourcePreferences> = emptyList(),
) {
    val isEmpty: Boolean get() = mangas.isEmpty() && categories.isEmpty()
}

@Serializable
data class Changeset(
    @ProtoNumber(1) val deviceId: String,
    @ProtoNumber(2) val baseSchema: Int = 1,
    @ProtoNumber(3) val cursor: Long,
    @ProtoNumber(4) val producedAt: Long,
    @ProtoNumber(5) val upserts: EntityDelta,
    @ProtoNumber(6) val tombstones: List<Tombstone> = emptyList(),
)
