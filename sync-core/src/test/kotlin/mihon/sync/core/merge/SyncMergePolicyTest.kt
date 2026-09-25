package mihon.sync.core.merge

import io.kotest.matchers.shouldBe
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import mihon.sync.core.model.categoryKey
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SyncMergePolicyTest {

    @Test
    fun `isIncomingWinner obeys watermark and deviceId tie breaker`() {
        // Watermark strictly higher
        SyncMergePolicy.isIncomingWinner(100L, "devA", 200L, "devA") shouldBe true
        SyncMergePolicy.isIncomingWinner(200L, "devB", 100L, "devA") shouldBe false

        // Watermark equal: tie-break by deviceId
        SyncMergePolicy.isIncomingWinner(100L, "devA", 100L, "devB") shouldBe true
        SyncMergePolicy.isIncomingWinner(100L, "devB", 100L, "devA") shouldBe false
        SyncMergePolicy.isIncomingWinner(100L, "devA", 100L, "devA") shouldBe false
    }

    @Test
    fun `mergeManga merges fields properly and applies LWW to favorite`() {
        val manga1 = AndroidBackupManga(
            source = 1L,
            url = "/manga/1",
            title = "Old Title",
            favorite = true,
            favoriteModifiedAt = 100L,
            genre = listOf("Action", "Comedy"),
            lastModifiedAt = 100L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch1",
                    name = "Chapter 1",
                    read = false,
                    lastPageRead = 5,
                    lastModifiedAt = 50L,
                ),
            ),
        )
        val manga2 = AndroidBackupManga(
            source = 1L,
            url = "/manga/1",
            title = "New Title",
            favorite = false,
            favoriteModifiedAt = 200L, // Newer favorite timestamp wins!
            genre = listOf("Comedy", "Drama"),
            lastModifiedAt = 200L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch1",
                    name = "Ch. 1",
                    read = true,
                    lastPageRead = 10,
                    lastModifiedAt = 150L,
                ),
                AndroidBackupChapter(
                    url = "/ch2",
                    name = "Chapter 2",
                    read = false,
                    lastModifiedAt = 150L,
                ),
            ),
        )

        val merged = SyncMergePolicy.mergeManga(manga1, "dev1", manga2, "dev2")
        merged.title shouldBe "New Title"
        merged.favorite shouldBe false // Newer favoriteModifiedAt wins!
        merged.favoriteModifiedAt shouldBe 200L
        merged.genre shouldBe listOf("Action", "Comedy", "Drama") // Union
        merged.lastModifiedAt shouldBe 200L
        merged.chapters.size shouldBe 2
        val ch1 = merged.chapters.first { it.url == "/ch1" }
        ch1.read shouldBe true // OR for read
        ch1.lastPageRead shouldBe 10L // maxOf
        ch1.lastModifiedAt shouldBe 150L
    }

    @Test
    fun `favorite LWW tie breaks on deviceId when timestamps equal`() {
        val manga1 = AndroidBackupManga(
            source = 1L,
            url = "/manga/fav",
            favorite = true,
            favoriteModifiedAt = 100L,
            lastModifiedAt = 100L,
        )
        val manga2 = AndroidBackupManga(
            source = 1L,
            url = "/manga/fav",
            favorite = false,
            favoriteModifiedAt = 100L,
            lastModifiedAt = 100L,
        )

        // "dev2" > "dev1" -> incoming wins -> favorite = false
        val merged1 = SyncMergePolicy.mergeManga(manga1, "dev1", manga2, "dev2")
        merged1.favorite shouldBe false

        // "dev1" < "dev2" -> incoming loses -> existing wins -> favorite = false
        val merged2 = SyncMergePolicy.mergeManga(manga2, "dev2", manga1, "dev1")
        merged2.favorite shouldBe false
    }

    @Test
    fun `ChapterKey parse handles URLs containing double colons`() {
        val keyStr = "123:/manga/tag?name=foo::bar::/ch1"
        val parsed = mihon.sync.core.model.ChapterKey.parse(keyStr)
        parsed.mangaKey.sourceId shouldBe 123L
        parsed.mangaKey.url shouldBe "/manga/tag?name=foo::bar"
        parsed.url shouldBe "/ch1"
    }

    @Test
    fun `CategoryKey equals and hashCode are case-insensitive`() {
        val cat1 = mihon.sync.core.model.CategoryKey("Action")
        val cat2 = mihon.sync.core.model.CategoryKey("action")
        val cat3 = mihon.sync.core.model.CategoryKey("ACTION")

        cat1 shouldBe cat2
        cat2 shouldBe cat3
        cat1.hashCode() shouldBe cat2.hashCode()
        setOf(cat1, cat2, cat3).size shouldBe 1
    }

    @Test
    fun `selectedString and selectedNullableString are commutative`() {
        // Blank incoming falling back to non-blank existing when incoming wins
        val res1 = SyncMergePolicy.selectedString(existing = "TitleA", incoming = "", incomingWins = true)
        val res2 = SyncMergePolicy.selectedString(existing = "", incoming = "TitleA", incomingWins = false)
        res1 shouldBe "TitleA"
        res2 shouldBe "TitleA"

        // Non-blank incoming winning
        val res3 = SyncMergePolicy.selectedString(existing = "TitleA", incoming = "TitleB", incomingWins = true)
        val res4 = SyncMergePolicy.selectedString(existing = "TitleB", incoming = "TitleA", incomingWins = false)
        res3 shouldBe "TitleB"
        res4 shouldBe "TitleB"

        // Nullable strings
        val res5 = SyncMergePolicy.selectedNullableString(existing = "ArtistA", incoming = null, incomingWins = true)
        val res6 = SyncMergePolicy.selectedNullableString(existing = null, incoming = "ArtistA", incomingWins = false)
        res5 shouldBe "ArtistA"
        res6 shouldBe "ArtistA"

        val res7 = SyncMergePolicy.selectedNullableString(existing = "ArtistA", incoming = "   ", incomingWins = true)
        val res8 = SyncMergePolicy.selectedNullableString(existing = "   ", incoming = "ArtistA", incomingWins = false)
        res7 shouldBe "ArtistA"
        res8 shouldBe "ArtistA"
    }

    @Test
    fun `tombstone deletedAt greater than or equal to manga removes upsert`() {
        val cs1 = Changeset(
            deviceId = "devA",
            cursor = 10L,
            producedAt = 1000L,
            upserts = EntityDelta(
                mangas = listOf(
                    AndroidBackupManga(source = 1L, url = "/manga/dead", lastModifiedAt = 100L),
                ),
            ),
        )
        val cs2 = Changeset(
            deviceId = "devB",
            cursor = 11L,
            producedAt = 1100L,
            upserts = EntityDelta(),
            tombstones = listOf(
                Tombstone(EntityType.MANGA, "1:/manga/dead", deletedAt = 150L),
            ),
        )

        val merged = SyncMergePolicy.mergeChangeset(cs1, cs2)
        merged.upserts.mangas shouldBe emptyList()
        merged.tombstones.size shouldBe 1
    }

    @Test
    fun `manga lastModifiedAt newer than tombstone revives manga`() {
        val cs1 = Changeset(
            deviceId = "devA",
            cursor = 10L,
            producedAt = 1000L,
            upserts = EntityDelta(),
            tombstones = listOf(
                Tombstone(EntityType.MANGA, "1:/manga/revived", deletedAt = 100L),
            ),
        )
        val cs2 = Changeset(
            deviceId = "devB",
            cursor = 12L,
            producedAt = 1200L,
            upserts = EntityDelta(
                mangas = listOf(
                    AndroidBackupManga(source = 1L, url = "/manga/revived", lastModifiedAt = 200L),
                ),
            ),
        )

        val merged = SyncMergePolicy.mergeChangeset(cs1, cs2)
        merged.upserts.mangas.size shouldBe 1
        merged.upserts.mangas.first().url shouldBe "/manga/revived"
        merged.tombstones shouldBe emptyList()
    }

    @Test
    fun `property test - commutativity and idempotence over randomized changesets`() {
        val rng = Random(42)

        for (i in 0 until 50) {
            val csA = randomChangeset(rng, "devA")
            val csB = randomChangeset(rng, "devB")

            // Idempotence
            val selfMergedA = SyncMergePolicy.mergeChangeset(csA, csA)
            selfMergedA.upserts.mangas shouldBe csA.upserts.mangas
            selfMergedA.upserts.categories shouldBe csA.upserts.categories
            selfMergedA.tombstones shouldBe csA.tombstones

            // Commutativity: merge(A, B) == merge(B, A)
            val mergedAB = SyncMergePolicy.mergeChangeset(csA, csB)
            val mergedBA = SyncMergePolicy.mergeChangeset(csB, csA)

            mergedAB.cursor shouldBe mergedBA.cursor
            mergedAB.baseSchema shouldBe mergedBA.baseSchema
            mergedAB.deviceId shouldBe mergedBA.deviceId
            mergedAB.upserts.mangas shouldBe mergedBA.upserts.mangas
            mergedAB.upserts.categories shouldBe mergedBA.upserts.categories
            mergedAB.tombstones shouldBe mergedBA.tombstones

            // Repeated merge stability: merge(merge(A, B), B) == merge(A, B)
            val mergedABB = SyncMergePolicy.mergeChangeset(mergedAB, csB)
            mergedABB.upserts.mangas shouldBe mergedAB.upserts.mangas
            mergedABB.upserts.categories shouldBe mergedAB.upserts.categories
            mergedABB.tombstones shouldBe mergedAB.tombstones
        }
    }

    private fun randomChangeset(rng: Random, deviceId: String): Changeset {
        val mangaCount = rng.nextInt(0, 4)
        val mangas = (0 until mangaCount).map { idx ->
            val sourceId = rng.nextLong(1, 3)
            val url = "/manga/$idx"
            val lastMod = rng.nextLong(10, 100)
            val favMod = if (rng.nextBoolean()) rng.nextLong(5, 100) else null
            val chapCount = rng.nextInt(0, 3)
            val chapters = (0 until chapCount).map { chIdx ->
                AndroidBackupChapter(
                    url = "/ch/$chIdx",
                    name = if (rng.nextInt(4) == 0) "" else "Chapter $chIdx",
                    scanlator = if (rng.nextBoolean()) null else "Scan $chIdx",
                    read = rng.nextBoolean(),
                    bookmark = rng.nextBoolean(),
                    lastPageRead = rng.nextLong(0, 20),
                    lastModifiedAt = rng.nextLong(5, 100),
                    sourceOrder = chIdx.toLong(),
                )
            }
            val histList = if (chapters.isNotEmpty() && rng.nextBoolean()) {
                listOf(
                    AndroidBackupHistory(
                        url = chapters.first().url,
                        lastRead = rng.nextLong(10, 100),
                        readDuration = rng.nextLong(10, 1000),
                    ),
                )
            } else {
                emptyList()
            }
            val trackList = if (rng.nextBoolean()) {
                listOf(
                    AndroidBackupTracking(
                        syncId = 1,
                        libraryId = 1L,
                        title = if (rng.nextBoolean()) "" else "Track Title",
                        lastChapterRead = rng.nextFloat() * 10f,
                        totalChapters = 20,
                    ),
                )
            } else {
                emptyList()
            }
            val titleVariant = when (rng.nextInt(4)) {
                0 -> ""
                1 -> "   "
                else -> "Manga $idx ($deviceId)"
            }
            AndroidBackupManga(
                source = sourceId,
                url = url,
                title = titleVariant,
                favorite = rng.nextBoolean(),
                favoriteModifiedAt = favMod,
                genre = listOf("Genre${rng.nextInt(1, 4)}"),
                lastModifiedAt = lastMod,
                chapters = chapters,
                history = histList,
                tracking = trackList,
                categories = listOf(rng.nextLong(0, 3)),
            )
        }

        val catBases = listOf(
            listOf("Action", "action", "ACTION"),
            listOf("Comedy", "comedy", "COMEDY"),
            listOf("Drama", "drama", "DRAMA"),
        )
        val catCount = rng.nextInt(0, 4)
        val categories = (0 until catCount).map { idx ->
            val variants = catBases[idx]
            AndroidBackupCategory(
                name = variants[rng.nextInt(variants.size)],
                order = rng.nextLong(0, 5),
                flags = rng.nextLong(0, 3),
            )
        }

        val tombstoneCount = rng.nextInt(0, 2)
        val tombstones = (0 until tombstoneCount).map { idx ->
            val isManga = rng.nextBoolean()
            // Use indices offset by mangaCount so a single changeset doesn't delete what it just created,
            // but when merged with another changeset (e.g. csB which has manga 0..3), they will conflict!
            val entityKey = if (isManga) {
                val tombIdx = idx + 2
                "1:/manga/$tombIdx"
            } else {
                "cat_deleted_$idx"
            }
            Tombstone(
                entityType = if (isManga) EntityType.MANGA else EntityType.CATEGORY,
                entityKey = entityKey,
                deletedAt = rng.nextLong(10, 200),
            )
        }.filter { tb ->
            // Filter out any tombstone that directly collides with an upsert in the same changeset
            mangas.none { m -> "1:${m.url}" == tb.entityKey }
        }

        val sortedTombstones = tombstones.sortedWith(
            compareBy<Tombstone> { it.entityType.ordinal }
                .thenBy(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR) { it.entityKey },
        )

        return Changeset(
            deviceId = deviceId,
            baseSchema = 1,
            cursor = rng.nextLong(1, 50),
            producedAt = rng.nextLong(1000, 2000),
            upserts = EntityDelta(
                mangas = mangas.sortedWith(
                    compareBy<AndroidBackupManga> {
                        it.source
                    }.thenBy(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR) { it.url },
                ),
                categories = categories.sortedWith(
                    compareBy(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR) { it.categoryKey.toKeyString() },
                ),
            ),
            tombstones = sortedTombstones,
        )
    }
}
