package mihon.desktop.track

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListViewerData(
    @SerialName("Viewer") val viewer: AniListViewer? = null,
)

@Serializable
internal data class AniListViewer(
    val id: Long = 0L,
    val name: String? = null,
)

@Serializable
internal data class AniListSearchData(
    @SerialName("Page") val page: AniListSearchPage? = null,
)

@Serializable
internal data class AniListSearchPage(
    val media: List<AniListMedia>? = null,
)

@Serializable
internal data class AniListMedia(
    val id: Long = 0L,
    val title: AniListTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val chapters: Long? = null,
    val description: String? = null,
)

@Serializable
internal data class AniListTitle(
    val userPreferred: String? = null,
)

@Serializable
internal data class AniListCoverImage(
    val large: String? = null,
)

@Serializable
internal data class AniListMediaListData(
    @SerialName("MediaList") val mediaList: AniListMediaListEntry? = null,
)

@Serializable
internal data class AniListMediaListEntry(
    val id: Long = 0L,
    val status: String? = null,
    val scoreRaw: Double? = null,
    val progress: Int = 0,
    @SerialName("private") val isPrivate: Boolean = false,
    val startedAt: AniListFuzzyDate? = null,
    val completedAt: AniListFuzzyDate? = null,
    val media: AniListMedia? = null,
)

@Serializable
internal data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
internal data class AniListSaveData(
    @SerialName("SaveMediaListEntry") val entry: AniListSaveEntry? = null,
)

@Serializable
internal data class AniListSaveEntry(
    val id: Long = 0L,
    val status: String? = null,
    val progress: Int = 0,
)
