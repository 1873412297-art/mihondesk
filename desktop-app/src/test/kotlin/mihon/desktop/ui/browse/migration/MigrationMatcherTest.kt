package mihon.desktop.ui.browse.migration

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class MigrationMatcherTest {

    @Test
    fun `normalizeTitle preserves letters and digits while ignoring case whitespace and symbols`() {
        MigrationMatcher.normalizeTitle("The  Apothecary-Diaries!") shouldBe "theapothecarydiaries"
        MigrationMatcher.normalizeTitle("葬送 的 芙莉莲") shouldBe "葬送的芙莉莲"
        MigrationMatcher.normalizeTitle("ONE_PIECE:  Vol. 1") shouldBe "onepiecevol1"
        MigrationMatcher.normalizeTitle("SPY x FAMILY") shouldBe "spyxfamily"
    }

    @Test
    fun `cleanTitle removes bracketed tags and punctuation`() {
        MigrationMatcher.cleanTitle("Frieren (Official)") shouldBe "frieren"
        MigrationMatcher.cleanTitle("One Piece [Digital Colored Comics]") shouldBe "one piece"
        MigrationMatcher.cleanTitle("Chainsaw Man {Scanlation}") shouldBe "chainsaw man"
        MigrationMatcher.cleanTitle("<HD> Jujutsu Kaisen") shouldBe "jujutsu kaisen"
    }

    @Test
    fun `exact match scores 1_0`() {
        MigrationMatcher.scoreMatch("One Piece", "One Piece") shouldBe 1.0
        MigrationMatcher.scoreMatch("One Piece", "one piece") shouldBe 1.0
        MigrationMatcher.scoreMatch("  One Piece  ", "One Piece") shouldBe 1.0
    }

    @Test
    fun `normalized match scores high confidence`() {
        val score = MigrationMatcher.scoreMatch("The Apothecary Diaries", "The  Apothecary-Diaries!")
        score shouldBeGreaterThanOrEqual 0.95

        val cjkScore = MigrationMatcher.scoreMatch("葬送的芙莉莲", "葬送 的 芙莉莲")
        cjkScore shouldBeGreaterThanOrEqual 0.95
    }

    @Test
    fun `cleaned title match with stripped brackets scores high confidence`() {
        val score = MigrationMatcher.scoreMatch("One Piece", "One Piece (Digital Colored)")
        score shouldBeGreaterThanOrEqual 0.90
    }

    @Test
    fun `unrelated title scores low`() {
        val score = MigrationMatcher.scoreMatch("One Piece", "Bleach")
        score shouldBeLessThan 0.35
    }

    @Test
    fun `evaluateCandidates selects unique high confidence match`() {
        val candidates = listOf(
            sManga("One Piece", "/manga/one-piece"),
            sManga("Bleach", "/manga/bleach"),
        )

        val eval = MigrationMatcher.evaluateCandidates("One Piece", candidates)
        eval.isUniqueHighConfidence shouldBe true
        eval.bestMatch?.manga?.title shouldBe "One Piece"
        eval.bestMatch?.isExactMatch shouldBe true
        eval.bestMatch?.isHighConfidence shouldBe true
    }

    @Test
    fun `evaluateCandidates flags ambiguous matches when top candidates are equally strong`() {
        val candidates = listOf(
            sManga("One Piece", "/manga/one-piece-1"),
            sManga("One Piece", "/manga/one-piece-2"),
        )

        val eval = MigrationMatcher.evaluateCandidates("One Piece", candidates)
        eval.isUniqueHighConfidence shouldBe false
        eval.candidates shouldHaveSize 2
        eval.candidates[0].score shouldBe 1.0
        eval.candidates[1].score shouldBe 1.0
    }

    @Test
    fun `evaluateCandidates handles empty candidate list`() {
        val eval = MigrationMatcher.evaluateCandidates("One Piece", emptyList())
        eval.isUniqueHighConfidence shouldBe false
        eval.bestMatch shouldBe null
        eval.candidates.shouldBeEmpty()
    }

    @Test
    fun `evaluateCandidates rejects low confidence top match`() {
        val candidates = listOf(
            sManga("Piece of Cake", "/manga/cake"),
            sManga("Another Title", "/manga/another"),
        )

        val eval = MigrationMatcher.evaluateCandidates("One Piece", candidates)
        eval.isUniqueHighConfidence shouldBe false
    }

    private fun sManga(title: String, url: String) = SManga(
        title = title,
        url = url,
    )
}
