package android.content

import android.app.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ContextDirectoryCompatibilityTest {
    @Test
    fun `external and auxiliary directories resolve under the profile and exist`(@TempDir directory: Path) {
        val base = directory.toFile()
        val app = Application(base)
        val ctx: Context = ContextWrapper(app)
        val dirs = listOf(
            ctx.getExternalCacheDir()!!,
            ctx.getExternalCacheDirs().single()!!,
            ctx.getExternalFilesDir(null)!!,
            ctx.getExternalFilesDir("Pictures")!!,
            ctx.getExternalFilesDirs(null).single()!!,
            ctx.getCodeCacheDir(),
            ctx.getNoBackupFilesDir(),
            ctx.getDataDir(),
            ctx.getDir("webview", Context.MODE_PRIVATE),
        )
        dirs.forEach { dir ->
            assertTrue(dir.isDirectory, "$dir")
            assertTrue(dir.canonicalPath.startsWith(base.canonicalPath), "$dir")
        }
        assertEquals(File(app.getCacheDir(), "external").canonicalFile, ctx.getExternalCacheDir()!!.canonicalFile)
        assertTrue(ctx.getExternalFilesDir("Pictures")!!.path.endsWith("Pictures"))
        assertEquals(File(ctx.getDataDir(), "databases/a.db").canonicalFile, ctx.getDatabasePath("a.db").canonicalFile)
        assertEquals(File(app.getFilesDir(), "x").canonicalFile, ctx.getFileStreamPath("x").canonicalFile)
    }
}
