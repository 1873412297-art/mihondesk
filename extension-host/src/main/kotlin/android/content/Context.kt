package android.content

import java.io.File

abstract class Context {
    companion object {
        const val MODE_PRIVATE = 0
    }

    abstract fun getPackageName(): String
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    abstract fun getCacheDir(): File
    abstract fun getFilesDir(): File

    // Extensions on Android often use external/auxiliary storage for caches. On the desktop host these
    // all resolve to profile-scoped directories under the per-extension data root.
    open fun getDataDir(): File = (getFilesDir().absoluteFile.parentFile ?: getFilesDir()).apply { mkdirs() }
    open fun getExternalCacheDir(): File? = File(getCacheDir(), "external").apply { mkdirs() }
    open fun getExternalCacheDirs(): Array<File?> = arrayOf(getExternalCacheDir())
    open fun getExternalFilesDir(type: String?): File? =
        File(getFilesDir(), if (type.isNullOrBlank()) "external" else "external/$type").apply { mkdirs() }
    open fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(getExternalFilesDir(type))
    open fun getCodeCacheDir(): File = File(getCacheDir(), "code_cache").apply { mkdirs() }
    open fun getNoBackupFilesDir(): File = File(getDataDir(), "no_backup").apply { mkdirs() }
    open fun getDir(name: String, mode: Int): File = File(getDataDir(), "app_$name").apply { mkdirs() }
    open fun getDatabasePath(name: String): File =
        File(File(getDataDir(), "databases").apply { mkdirs() }, name)
    open fun getFileStreamPath(name: String): File = File(getFilesDir(), name)
    open fun getAssets(): android.content.res.AssetManager = android.content.res.AssetManager(
        File(getFilesDir(), "assets"),
    )
    open fun getApplicationContext(): Context = this
    open fun getResources(): android.content.res.Resources = android.content.res.Resources.getSystem()
    open fun startActivity(
        intent: Intent,
    ): Unit = throw ActivityNotFoundException(
        "Android activities are unsupported; open the source in the desktop browser",
    )
}
