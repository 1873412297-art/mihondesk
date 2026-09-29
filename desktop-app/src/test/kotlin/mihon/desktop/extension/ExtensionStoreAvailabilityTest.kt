package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionStoreAvailabilityTest {

    private val store = "https://example.com/store"
    private val item = ExtensionStoreItem(
        pkg = "ext.one",
        name = "One",
        version = "1.0",
        versionCode = 1L,
        downloadUrl = "$store/ext.one.mext",
        repoUrl = store,
    )

    @Test
    fun `a listed candidate from a configured store stays installable`() {
        isStoreCandidateInstallable(item, listOf(store), listOf(item)) shouldBe true
    }

    @Test
    fun `a listing without a package to download is not installable`() {
        isStoreCandidateInstallable(item.copy(downloadUrl = ""), listOf(store), listOf(item)) shouldBe false
    }

    @Test
    fun `a candidate whose store was removed is not installable`() {
        isStoreCandidateInstallable(item, listOf("https://example.com/other-store"), listOf(item)) shouldBe false
        isStoreCandidateInstallable(item, emptyList(), listOf(item)) shouldBe false
    }

    @Test
    fun `a package no configured store lists anymore is not installable`() {
        val other = item.copy(pkg = "ext.other")
        isStoreCandidateInstallable(item, listOf(store), listOf(other)) shouldBe false
        isStoreCandidateInstallable(item, listOf(store), emptyList()) shouldBe false
    }

    @Test
    fun `items that are not store candidates are never blocked`() {
        isStoreCandidateInstallable(item.copy(repoUrl = ""), emptyList(), emptyList()) shouldBe true
    }

    @Test
    fun `trailing slashes do not change store identity`() {
        isStoreCandidateInstallable(item, listOf("$store/"), listOf(item)) shouldBe true
    }
}
