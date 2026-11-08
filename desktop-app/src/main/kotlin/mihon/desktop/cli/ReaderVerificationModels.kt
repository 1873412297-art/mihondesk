package mihon.desktop.cli

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ReaderVerificationSummary(
    val command: String = "verify-reader",
    val status: String = "SUCCEEDED",
    val phase: String,
    val fixtureManifestSha256: String,
    val verifiedAssets: List<String>,
    val verifiedModes: List<String>,
    val decodedTileCount: Int,
    val gifFrameHashes: List<String>,
    val cacheResidentHighWaterBytes: Long,
    val coreResidentAndInFlightHighWaterBytes: Long,
    val progressRows: List<ReaderProgressRow>,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val soak: ReaderSoakSummary? = null,
)

@Serializable
data class ReaderProgressRow(
    val chapterId: Long,
    val chapterName: String,
    val pageIndex: Long,
    val pageCount: Int,
    val completed: Boolean,
)

@Serializable
internal data class ReaderFixtureManifest(
    val format: String,
    val mangaDirectory: String,
    val standaloneImage: String,
    val committedRarPath: String,
    val committedRarSha256: String,
    val committedSourcePath: String,
    val committedSourceSha256: String,
    val files: List<ReaderFixtureFileRecord>,
)

@Serializable
internal data class ReaderFixtureFileRecord(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)
