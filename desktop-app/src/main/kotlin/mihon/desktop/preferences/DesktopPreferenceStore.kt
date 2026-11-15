package mihon.desktop.preferences

import mihon.desktop.i18n.AppLanguage
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.PinHasher
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.window.WindowPlacement
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

class DesktopPreferenceStore(private val file: Path) {

    private data class FileStamp(val lastModifiedMillis: Long, val size: Long)

    @Volatile
    private var cachedProperties: Properties? = null

    @Volatile
    private var cachedPreferences: DesktopPreferences? = null

    @Volatile
    private var cacheStamp: FileStamp? = null

    @Synchronized
    fun invalidateCache() {
        cachedProperties = null
        cachedPreferences = null
        cacheStamp = null
    }

    private fun currentStamp(): FileStamp = try {
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        FileStamp(attributes.lastModifiedTime().toMillis(), attributes.size())
    } catch (_: IOException) {
        FileStamp(-1L, -1L)
    }

    private fun getOrReadProperties(): Properties {
        // A single stat per read keeps the cache honest across instances and external
        // writers while still avoiding a full file read + parse on hot paths.
        val stamp = currentStamp()
        if (stamp == cacheStamp) cachedProperties?.let { return it }
        synchronized(this) {
            val secondStamp = currentStamp()
            if (secondStamp == cacheStamp) cachedProperties?.let { return it }
            val loaded = readProperties()
            cachedProperties = loaded
            cacheStamp = secondStamp
            return loaded
        }
    }

    /** Apply an edit to the latest snapshot under the same lock as other preference writers. */
    @Synchronized
    fun updatePreferences(transform: (DesktopPreferences) -> DesktopPreferences): DesktopPreferences {
        val updated = transform(load())
        save(updated)
        return updated
    }

    @Synchronized
    fun load(): DesktopPreferences {
        if (currentStamp() == cacheStamp) cachedPreferences?.let { return it }
        val loadedPreferences = getOrReadProperties().readDesktopPreferences()
        cachedPreferences = loadedPreferences
        return loadedPreferences
    }

    @Synchronized
    fun save(preferences: DesktopPreferences) {
        val properties = Properties().apply { putAll(getOrReadProperties()) }
        properties.writeDesktopPreferences(preferences)
        writeProperties(properties)
        cachedProperties = properties
        cachedPreferences = preferences
        cacheStamp = currentStamp()
    }

    @Synchronized
    fun property(key: String): String? = getOrReadProperties().getProperty(key)

    @Synchronized
    fun propertiesWithPrefix(prefix: String): Map<String, String> = getOrReadProperties().let { properties ->
        properties.stringPropertyNames().filter { it.startsWith(prefix) }
            .associateWith { properties.getProperty(it) }
    }

    @Synchronized
    fun update(block: Properties.() -> Unit) {
        val properties = Properties().apply { putAll(getOrReadProperties()) }
        properties.apply(block)
        writeProperties(properties)
        cachedProperties = properties
        cachedPreferences = null
        cacheStamp = currentStamp()
    }

    private fun readProperties(): Properties {
        if (!Files.exists(file)) return Properties()
        return try {
            Properties().apply {
                Files.newInputStream(file).use { input -> load(input) }
            }
        } catch (error: IOException) {
            return recoverCorruptPreferences(file, error, ::invalidateCache)
        } catch (error: IllegalArgumentException) {
            return recoverCorruptPreferences(file, error, ::invalidateCache)
        }
    }

    private fun writeProperties(properties: Properties) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }
}
