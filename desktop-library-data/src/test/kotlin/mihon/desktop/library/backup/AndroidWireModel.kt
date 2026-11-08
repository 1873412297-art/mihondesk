package mihon.desktop.library.backup

/** The shapes Android's `Backup` model serialises, named after its fields rather than the desktop model. */
data class WireBackup(
    val mangas: List<WireManga> = emptyList(),
    val categories: List<WireCategory> = emptyList(),
    val sources: List<WireSource> = emptyList(),
    val preferences: List<WirePreference> = emptyList(),
)

data class WireCategory(val name: String, val order: Long, val id: Long, val flags: Long = 0)

data class WireSource(val name: String, val sourceId: Long)

data class WirePreference(val key: String, val typeUrl: String, val valueBytes: ByteArray) {
    companion object {
        fun readFrom(reader: ProtoReader): WirePreference {
            var key = ""
            var typeUrl = ""
            var valueBytes = ByteArray(0)
            while (reader.hasMore) {
                when (reader.nextField()) {
                    1 -> key = reader.readString()
                    2 -> ProtoReader(reader.readBytes()).let { any ->
                        while (any.hasMore) {
                            when (any.nextField()) {
                                1 -> typeUrl = any.readString()
                                2 -> valueBytes = any.readBytes()
                                else -> any.skip()
                            }
                        }
                    }
                    else -> reader.skip()
                }
            }
            return WirePreference(key, typeUrl, valueBytes)
        }
    }
}

data class WireChapter(
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val chapterNumber: Float,
    val sourceOrder: Long,
    val lastModifiedAt: Long,
    val memo: String,
) {
    fun writeTo(writer: ProtoWriter) {
        writer.string(1, url)
        writer.string(2, name)
        writer.string(3, scanlator)
        writer.bool(4, read)
        writer.bool(5, bookmark)
        writer.long(6, lastPageRead)
        writer.float(9, chapterNumber)
        writer.long(10, sourceOrder)
        writer.long(11, lastModifiedAt)
        writer.bytes(13, memo.encodeToByteArray())
    }

    companion object {
        fun readFrom(reader: ProtoReader): WireChapter {
            var url = ""
            var name = ""
            var scanlator: String? = null
            var read = false
            var bookmark = false
            var lastPageRead = 0L
            var chapterNumber = 0f
            var sourceOrder = 0L
            var lastModifiedAt = 0L
            var memo = ""
            while (reader.hasMore) {
                when (reader.nextField()) {
                    1 -> url = reader.readString()
                    2 -> name = reader.readString()
                    3 -> scanlator = reader.readString()
                    4 -> read = reader.readBool()
                    5 -> bookmark = reader.readBool()
                    6 -> lastPageRead = reader.readLong()
                    9 -> chapterNumber = reader.readFloat()
                    10 -> sourceOrder = reader.readLong()
                    11 -> lastModifiedAt = reader.readLong()
                    13 -> memo = reader.readBytes().decodeToString()
                    else -> reader.skip()
                }
            }
            return WireChapter(
                url = url,
                name = name,
                scanlator = scanlator,
                read = read,
                bookmark = bookmark,
                lastPageRead = lastPageRead,
                chapterNumber = chapterNumber,
                sourceOrder = sourceOrder,
                lastModifiedAt = lastModifiedAt,
                memo = memo,
            )
        }
    }
}

data class WireManga(
    val source: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: Int,
    val thumbnailUrl: String?,
    val dateAdded: Long,
    val chapters: List<WireChapter>,
    val categoryOrder: List<Long>,
    val favorite: Boolean,
    val historyLastRead: Map<String, Long>,
    val updateStrategy: Int,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val notes: String,
    val initialized: Boolean,
    val memo: String,
) {
    fun writeTo(writer: ProtoWriter) {
        writer.long(1, source)
        writer.string(2, url)
        writer.string(3, title)
        writer.string(4, artist)
        writer.string(5, author)
        writer.string(6, description)
        genre.forEach { writer.string(7, it) }
        writer.int(8, status)
        writer.string(9, thumbnailUrl)
        writer.long(13, dateAdded)
        chapters.forEach { chapter -> writer.message(16) { chapter.writeTo(this) } }
        categoryOrder.forEach { writer.long(17, it) }
        writer.bool(100, favorite)
        historyLastRead.forEach { (chapterUrl, lastRead) ->
            writer.message(104) {
                string(1, chapterUrl)
                long(2, lastRead)
            }
        }
        writer.int(105, updateStrategy)
        writer.long(106, lastModifiedAt)
        favoriteModifiedAt?.let { writer.long(107, it) }
        writer.long(109, version)
        writer.string(110, notes)
        writer.bool(111, initialized)
        writer.bytes(112, memo.encodeToByteArray())
    }

    companion object {
        fun readFrom(reader: ProtoReader): WireManga {
            val fields = MutableWireManga()
            while (reader.hasMore) fields.readField(reader)
            return fields.toManga()
        }

        /**
         * A field-by-field sink for [readFrom]. Splitting the `when` from the constructor call keeps
         * each piece short enough to read on its own.
         */
        private class MutableWireManga {
            var source = 0L
            var url = ""
            var title = ""
            var artist: String? = null
            var author: String? = null
            var description: String? = null
            val genre = mutableListOf<String>()
            var status = 0
            var thumbnailUrl: String? = null
            var dateAdded = 0L
            val chapters = mutableListOf<WireChapter>()
            val categoryOrder = mutableListOf<Long>()

            // Android declares `favorite` with a default of true, and ProtoBuf omits fields equal to
            // their default, so an absent field means "favorite", not "not favorite".
            var favorite = true
            val history = mutableMapOf<String, Long>()
            var updateStrategy = 0
            var lastModifiedAt = 0L
            var favoriteModifiedAt: Long? = null
            var version = 0L
            var notes = ""
            var initialized = false
            var memo = ""

            fun readField(reader: ProtoReader) {
                when (reader.nextField()) {
                    1 -> source = reader.readLong()
                    2 -> url = reader.readString()
                    3 -> title = reader.readString()
                    4 -> artist = reader.readString()
                    5 -> author = reader.readString()
                    6 -> description = reader.readString()
                    7 -> genre += reader.readString()
                    8 -> status = reader.readLong().toInt()
                    9 -> thumbnailUrl = reader.readString()
                    13 -> dateAdded = reader.readLong()
                    16 -> chapters += WireChapter.readFrom(ProtoReader(reader.readBytes()))
                    17 -> categoryOrder += reader.readLong()
                    100 -> favorite = reader.readBool()
                    104 -> readHistoryEntry(ProtoReader(reader.readBytes()))
                    105 -> updateStrategy = reader.readLong().toInt()
                    106 -> lastModifiedAt = reader.readLong()
                    107 -> favoriteModifiedAt = reader.readLong()
                    109 -> version = reader.readLong()
                    110 -> notes = reader.readString()
                    111 -> initialized = reader.readBool()
                    112 -> memo = reader.readBytes().decodeToString()
                    else -> reader.skip()
                }
            }

            fun toManga(): WireManga = WireManga(
                source = source,
                url = url,
                title = title,
                artist = artist,
                author = author,
                description = description,
                genre = genre,
                status = status,
                thumbnailUrl = thumbnailUrl,
                dateAdded = dateAdded,
                chapters = chapters,
                categoryOrder = categoryOrder,
                favorite = favorite,
                historyLastRead = history,
                updateStrategy = updateStrategy,
                lastModifiedAt = lastModifiedAt,
                favoriteModifiedAt = favoriteModifiedAt,
                version = version,
                notes = notes,
                initialized = initialized,
                memo = memo,
            )

            private fun readHistoryEntry(entry: ProtoReader) {
                var chapterUrl = ""
                var lastRead = 0L
                while (entry.hasMore) {
                    when (entry.nextField()) {
                        1 -> chapterUrl = entry.readString()
                        2 -> lastRead = entry.readLong()
                        else -> entry.skip()
                    }
                }
                history[chapterUrl] = lastRead
            }
        }
    }
}
