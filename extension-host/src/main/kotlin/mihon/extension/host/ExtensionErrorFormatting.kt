package mihon.extension.host

/** Preserves the extension-side failure class and source location in the IPC error response. */
internal fun formatExtensionError(error: Throwable): String {
    val className = error::class.java.simpleName
    val topFrame = error.stackTrace.firstOrNull { frame ->
        val name = frame.className
        !name.startsWith("mihon.extension.host.ExtensionHostEngine") &&
            !name.startsWith("kotlinx.coroutines.") &&
            !name.startsWith("java.lang.reflect.") &&
            !name.startsWith("jdk.internal.")
    } ?: error.stackTrace.firstOrNull()
    val location = topFrame?.let(::formatSourceLocation).orEmpty()
    val message = error.message
    return when {
        message.isNullOrBlank() -> "$className$location"
        message.startsWith(className) -> "$message$location"
        else -> "$className: $message$location"
    }
}

private fun formatSourceLocation(frame: StackTraceElement): String {
    val fileAndLine = when {
        frame.fileName != null && frame.lineNumber > 0 -> "(${frame.fileName}:${frame.lineNumber})"
        frame.fileName != null -> "(${frame.fileName})"
        else -> ""
    }
    return " at ${frame.className}.${frame.methodName}$fileAndLine"
}
