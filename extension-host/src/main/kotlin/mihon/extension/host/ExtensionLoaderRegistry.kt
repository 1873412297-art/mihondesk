package mihon.extension.host

import java.util.concurrent.ConcurrentHashMap

/** Owns each extension's class loader so replacement and host shutdown release its jar handles. */
internal class ExtensionLoaderRegistry : AutoCloseable {
    private val loaders = ConcurrentHashMap<String, ExtensionClassLoader>()

    fun replace(packageId: String, loader: ExtensionClassLoader) {
        loaders.put(packageId, loader)?.let { previous -> close(previous, packageId) }
    }

    fun closeAndRemove(packageId: String) {
        loaders.remove(packageId)?.let { close(it, packageId) }
    }

    private fun close(loader: ExtensionClassLoader, packageId: String) {
        try {
            loader.close()
        } catch (error: Exception) {
            System.err.println("Extension loader could not close for $packageId: ${error.message}")
        }
    }

    override fun close() {
        loaders.forEach { (packageId, loader) -> close(loader, packageId) }
        loaders.clear()
    }
}
