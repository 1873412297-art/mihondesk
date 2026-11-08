package mihon.desktop.library.backup

/**
 * A hand-rolled ProtoBuf codec that speaks the wire format of the **Android** backup model in
 * `app/src/main/java/eu/kanade/tachiyomi/data/backup/models`.
 *
 * Every field number below is transcribed from those classes. This file shares no code and no
 * annotations with `mihon.sync.core.model`, so if a desktop `@ProtoNumber` drifts away from
 * Android's, the compatibility test fails instead of the desktop encoder validating itself.
 *
 * The message shapes live in `AndroidWireModel.kt` and the raw protobuf primitives in `ProtoWire.kt`.
 */
object AndroidWireFormat {
    // Backup
    const val BACKUP_MANGA = 1
    const val BACKUP_CATEGORY = 2
    const val BACKUP_SOURCE = 101
    const val BACKUP_PREFERENCE = 104
    const val BACKUP_EXTENSION_STORE = 106

    fun encode(backup: WireBackup): ByteArray = ProtoWriter().apply {
        backup.mangas.forEach { manga -> message(BACKUP_MANGA) { manga.writeTo(this) } }
        backup.categories.forEach { category ->
            message(BACKUP_CATEGORY) {
                string(1, category.name)
                long(2, category.order)
                long(3, category.id)
                long(100, category.flags)
            }
        }
        backup.sources.forEach { source ->
            message(BACKUP_SOURCE) {
                string(1, source.name)
                long(2, source.sourceId)
            }
        }
        backup.preferences.forEach { preference ->
            message(BACKUP_PREFERENCE) {
                string(1, preference.key)
                // kotlinx ProtoBuf encodes a polymorphic value as an Any message:
                // field 1 = fully qualified type URL, field 2 = the concrete value's bytes.
                message(2) {
                    string(1, preference.typeUrl)
                    bytes(2, preference.valueBytes)
                }
            }
        }
    }.toByteArray()

    fun decode(bytes: ByteArray): WireBackup {
        val reader = ProtoReader(bytes)
        val mangas = mutableListOf<WireManga>()
        val categories = mutableListOf<WireCategory>()
        val sources = mutableListOf<WireSource>()
        val preferences = mutableListOf<WirePreference>()
        while (reader.hasMore) {
            when (reader.nextField()) {
                BACKUP_MANGA -> mangas += WireManga.readFrom(ProtoReader(reader.readBytes()))
                BACKUP_CATEGORY -> ProtoReader(reader.readBytes()).let { category ->
                    val fields = category.collect()
                    categories += WireCategory(
                        name = fields.string(1).orEmpty(),
                        order = fields.long(2),
                        id = fields.long(3),
                        flags = fields.long(100),
                    )
                }
                BACKUP_SOURCE -> ProtoReader(reader.readBytes()).let { source ->
                    val fields = source.collect()
                    sources += WireSource(name = fields.string(1).orEmpty(), sourceId = fields.long(2))
                }
                BACKUP_PREFERENCE -> preferences += WirePreference.readFrom(ProtoReader(reader.readBytes()))
                else -> reader.skip()
            }
        }
        return WireBackup(mangas, categories, sources, preferences)
    }

    /** A String preference value as Android's `StringPreferenceValue` sees it. */
    fun stringPreferenceValue(value: String): ByteArray = ProtoWriter().apply { string(1, value) }.toByteArray()

    const val STRING_PREFERENCE_TYPE = "eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue"
}
