package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionStoreLabelTest {

    @Test
    fun `declared store name wins over the repository url`() {
        extensionStoreLabel("Keiyoushi", "https://raw.githubusercontent.com/keiyoushi/extensions/repo") shouldBe
            "Keiyoushi"
    }

    @Test
    fun `github repository urls are reduced to owner and repository`() {
        extensionStoreLabel("", "https://raw.githubusercontent.com/keiyoushi/extensions/repo") shouldBe
            "keiyoushi/extensions"
        extensionStoreLabel("", "https://github.com/some-user/extensions.git") shouldBe "some-user/extensions"
        extensionStoreLabel("", "https://github.com/some-user/extensions/tree/main/index") shouldBe
            "some-user/extensions"
    }

    @Test
    fun `other repositories keep their host and path so two stores never share a label`() {
        extensionStoreLabel("", "https://example.com/good-repo/") shouldBe "example.com/good-repo"
        extensionStoreLabel("", "https://repo.mihon.app") shouldBe "repo.mihon.app"
    }

    @Test
    fun `unknown store is null and unparsable urls are shown as given`() {
        extensionStoreLabel("", "") shouldBe null
        extensionStoreLabel("  ", "  ") shouldBe null
        extensionStoreLabel("", "not a url") shouldBe "not a url"
    }

    @Test
    fun `items carry the label of the store that listed them`() {
        val item = ExtensionStoreItem(
            pkg = "ext.one",
            name = "One",
            version = "1.0",
            versionCode = 1L,
            repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        )

        extensionStoreLabel(item) shouldBe "keiyoushi/extensions"
        extensionStoreLabel(item.copy(storeName = "Keiyoushi")) shouldBe "Keiyoushi"
        extensionStoreLabel(item.copy(repoUrl = "")) shouldBe null
    }
}
