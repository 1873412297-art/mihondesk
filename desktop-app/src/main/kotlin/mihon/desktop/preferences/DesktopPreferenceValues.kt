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

internal fun Properties.readWindowPlacement(): WindowPlacement? {
    val x = getProperty("window.x")?.toIntOrNull() ?: return null
    val y = getProperty("window.y")?.toIntOrNull() ?: return null
    val width = getProperty("window.width")?.toIntOrNull() ?: return null
    val height = getProperty("window.height")?.toIntOrNull() ?: return null
    val maximized = getProperty("window.maximized")?.toBooleanStrictOrNull() ?: false
    return WindowPlacement(x, y, width, height, maximized)
}

internal fun Properties.firstInt(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { key -> getProperty(key)?.toIntOrNull() }

internal fun Properties.firstBoolean(vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { key -> getProperty(key)?.toBooleanStrictOrNull() }

internal fun Properties.firstLongSet(vararg keys: String): Set<Long> {
    val raw = keys.firstNotNullOfOrNull(::getProperty) ?: return emptySet()
    return raw.trim().removeSurrounding("[", "]")
        .split(',', ';')
        .asSequence()
        .map { it.trim().trim('"', '\'', ' ') }
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L }
        .toSet()
}

internal fun Set<Long>.encodeLongSet(): String = asSequence()
    .filter { it > 0L }
    .distinct()
    .sorted()
    .joinToString(",")

internal inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: default
}

/** Keys left behind by the removed LAN sync server; stripped on the next save. */
internal val LEGACY_SYNC_SERVER_KEYS = listOf(
    "sync.server_enabled",
    "sync.server_port",
    "sync.server_token",
    "sync.server_quick_pair",
    "sync.server_device_name",
    "sync.server_selected_ip",
)
