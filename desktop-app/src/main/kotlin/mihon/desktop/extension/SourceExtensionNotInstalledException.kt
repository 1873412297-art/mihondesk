package mihon.desktop.extension

/** No installed extension provides [sourceId] (e.g. a backup restored manga without their extensions). */
class SourceExtensionNotInstalledException(val sourceId: Long) :
    IllegalStateException("Source extension is not installed (source $sourceId)")
