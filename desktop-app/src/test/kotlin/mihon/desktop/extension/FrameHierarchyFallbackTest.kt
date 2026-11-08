package mihon.desktop.extension

import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.desktop.extension.compat.frameParents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FrameHierarchyFallbackTest {
    private val loader = javaClass.classLoader

    @Test
    fun `android framework classes missing on desktop merge as plain objects instead of failing the install`() {
        // Real APKs reference android/text/StaticLayout and android/os/LocaleList in unused branches.
        assertEquals(
            "java/lang/Object" to emptyList<String>(),
            TachiyomiExtensionConverter.frameParents("android/text/StaticLayout", loader),
        )
        assertEquals(
            "java/lang/Object" to emptyList<String>(),
            TachiyomiExtensionConverter.frameParents("android/os/LocaleList", loader),
        )
    }

    @Test
    fun `known classes keep their real hierarchy and unknown non-android types still fail`() {
        assertEquals("java/lang/Number", TachiyomiExtensionConverter.frameParents("java/lang/Integer", loader).first)
        assertThrows(IllegalArgumentException::class.java) {
            TachiyomiExtensionConverter.frameParents("com/missing/Thing", loader)
        }
    }
}
