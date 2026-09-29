package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.extension.model.ExtensionManifest
import org.junit.jupiter.api.Test

class ExtensionStoreSelectionTest {
    @Test
    fun `higher version from a different signer cannot replace matching store update`() {
        val installed = InstalledExtension(
            pkg = "ext.one",
            manifest = ExtensionManifest("ext.one", "One", "1.0", 10L, 1.4, "en", sources = emptyList()),
            installDir = "one",
            packageFile = "one.mext",
            installedAt = 0,
            signatureFingerprint = "aa11",
        )
        val matching = ExtensionStoreItem("ext.one", "One", "1.1", 11L, signingKey = "AA:11")
        val mismatched = ExtensionStoreItem("ext.one", "One", "9.0", 90L, signingKey = "bb22")

        selectAvailableStoreItems(listOf(installed), listOf(mismatched, matching)) shouldBe listOf(matching)
        selectAvailableStoreItems(listOf(installed), listOf(mismatched)) shouldBe emptyList()
    }

    @Test
    fun `the selected candidate keeps the store it came from when two stores list the same package`() {
        val installed = InstalledExtension(
            pkg = "ext.two",
            manifest = ExtensionManifest("ext.two", "Two", "1.0", 10L, 1.4, "en", sources = emptyList()),
            installDir = "two",
            packageFile = "two.mext",
            installedAt = 0,
            signatureFingerprint = "aa11",
        )
        val signer = "AA:11"
        val firstStore = ExtensionStoreItem(
            pkg = "ext.two",
            name = "Two",
            version = "1.1",
            versionCode = 11L,
            repoUrl = "https://raw.githubusercontent.com/first/store/repo",
            storeName = "First Store",
            signingKey = signer,
        )
        val secondStore = ExtensionStoreItem(
            pkg = "ext.two",
            name = "Two",
            version = "1.2",
            versionCode = 12L,
            repoUrl = "https://raw.githubusercontent.com/second/store/repo",
            storeName = "Second Store",
            signingKey = signer,
        )

        val selected = selectAvailableStoreItems(listOf(installed), listOf(firstStore, secondStore))

        // The label must describe the store the shown candidate came from, not the first configured one.
        selected shouldBe listOf(secondStore)
        extensionStoreLabel(selected.single()) shouldBe "Second Store"
        extensionStoreLabel(selected.single().copy(storeName = "")) shouldBe "second/store"
    }

    @Test
    fun `the store label follows the candidate that survives signer filtering`() {
        val installed = InstalledExtension(
            pkg = "ext.three",
            manifest = ExtensionManifest("ext.three", "Three", "1.0", 10L, 1.4, "en", sources = emptyList()),
            installDir = "three",
            packageFile = "three.mext",
            installedAt = 0,
            signatureFingerprint = "aa11",
        )
        val newerFromAnotherSigner = ExtensionStoreItem(
            pkg = "ext.three",
            name = "Three",
            version = "9.0",
            versionCode = 90L,
            repoUrl = "https://example.com/other-store",
            storeName = "Other Store",
            signingKey = "bb22",
        )
        val matching = ExtensionStoreItem(
            pkg = "ext.three",
            name = "Three",
            version = "1.1",
            versionCode = 11L,
            repoUrl = "https://example.com/matching-store",
            storeName = "Matching Store",
            signingKey = "AA:11",
        )

        val selected = selectAvailableStoreItems(listOf(installed), listOf(newerFromAnotherSigner, matching))

        selected shouldBe listOf(matching)
        extensionStoreLabel(selected.single()) shouldBe "Matching Store"
    }
}
