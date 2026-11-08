package mihon.extension.host

import java.io.InputStream

internal fun Class<*>.binaryResourceName(): String = name.replace('.', '/') + ".class"

internal fun Class<*>.readResourceBytes(): ByteArray =
    (getResourceAsStream("/" + binaryResourceName()) ?: error("Missing class bytes for $name"))
        .use(InputStream::readBytes)
