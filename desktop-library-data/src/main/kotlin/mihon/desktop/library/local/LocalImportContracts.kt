package mihon.desktop.library.local

import java.nio.file.Path

internal fun interface LocalStagingCheckpoint {
    fun afterStagingCopy(stagingPath: Path)

    companion object {
        val NONE = LocalStagingCheckpoint {}
    }
}

internal interface LocalFileFaults {
    fun afterCopyChunk(source: Path, target: Path, copiedBytes: Long) = Unit
    fun beforeAtomicMove(source: Path, target: Path) = Unit
    fun afterAtomicMove(source: Path, target: Path) = Unit
    fun afterClaimCreated(claim: Path) = Unit
    fun afterStagingDirectoryCreated(directory: Path) = Unit
    fun duringMarkerPublication(temporary: Path, marker: Path) = Unit

    companion object {
        val NONE = object : LocalFileFaults {}
    }
}

/** Test-only seam that models process death by intentionally bypassing synchronous compensation. */
internal class LocalImportCrashSimulation : Error("simulated local import process crash")
