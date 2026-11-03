package mihon.desktop.extension

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopCookieStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty store returns null`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        assertNull(store.getCookieHeader("example.com"))
        assertNull(store.getUserAgent("example.com"))
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun `stores cookies and matches parent and subdomains`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        store.setCookies(
            domain = "mangadex.org",
            cookies = mapOf("cf_clearance" to "secret123", "session" to "abc"),
            customUserAgent = "CustomUA/1.0",
        )

        // Exact match
        assertEquals("cf_clearance=secret123; session=abc", store.getCookieHeader("mangadex.org"))
        assertEquals("CustomUA/1.0", store.getUserAgent("mangadex.org"))

        // Subdomain match
        assertEquals("cf_clearance=secret123; session=abc", store.getCookieHeader("api.mangadex.org"))
        assertEquals("CustomUA/1.0", store.getUserAgent("uploads.mangadex.org"))

        // Unrelated domain
        assertNull(store.getCookieHeader("other.com"))
        assertNull(store.getUserAgent("other.com"))

        // Verify file persisted
        assertTrue(Files.exists(file))

        // Reload store from disk
        val reloadedStore = DesktopCookieStore(file)
        assertEquals("cf_clearance=secret123; session=abc", reloadedStore.getCookieHeader("mangadex.org"))
        assertEquals("CustomUA/1.0", reloadedStore.getUserAgent("mangadex.org"))
    }

    @Test
    fun `parseRawCookies handles standard and messy cookie strings`() {
        val raw = "cf_clearance=token_xyz;  _ga=GA1.2.345; session_id=9876 ;  "
        val parsed = DesktopCookieStore.parseRawCookies(raw)

        assertEquals(3, parsed.size)
        assertEquals("token_xyz", parsed["cf_clearance"])
        assertEquals("GA1.2.345", parsed["_ga"])
        assertEquals("9876", parsed["session_id"])
    }

    @Test
    fun `browser user agent fills a blank one and does not replace a saved one`() {
        val store = DesktopCookieStore(tempDir.resolve("cookies.json"))

        store.setCookies("reader.hipmh.top", mapOf("k" to "v"))
        store.noteBrowserUserAgent("reader.hipmh.top", "Chrome/Real")
        assertEquals("k=v", store.getCookieHeader("reader.hipmh.top"))
        assertEquals("Chrome/Real", store.getUserAgent("reader.hipmh.top"))

        store.noteBrowserUserAgent("m.hipmh.com", "  ")
        assertNull(store.getUserAgent("m.hipmh.com"))
        store.noteBrowserUserAgent("m.hipmh.com", "Chrome/Real")
        assertEquals("Chrome/Real", store.getUserAgent("m.hipmh.com"))

        store.setCookies("example.com", mapOf("a" to "b"), "HandCopied/1")
        store.noteBrowserUserAgent("example.com", "Chrome/Real")
        assertEquals("HandCopied/1", store.getUserAgent("example.com"))
        assertEquals("a=b", store.getCookieHeader("example.com"))
    }

    @Test
    fun `loadAll returns every live cookie for that extension`() {
        val store = DesktopCookieStore(tempDir.resolve("sessions.json"))
        val cookie = Cookie.Builder().name("session").value("v").domain("hipmh.com").path("/")
            .secure().httpOnly().expiresAt(System.currentTimeMillis() + 60_000).build()
        store.saveFromResponse("hipmh", "https://m.hipmh.com/".toHttpUrl(), listOf(cookie))

        assertEquals(listOf("session"), store.loadAll("hipmh").map { it.name })
        assertTrue(store.loadAll("other").isEmpty())
    }

    @Test
    fun `removeCookies clears domain and updates disk`() {
        val file = tempDir.resolve("cookies.json")
        val store = DesktopCookieStore(file)

        store.setCookies("example.com", mapOf("key" to "val"))
        assertNotNull(store.getCookieHeader("example.com"))

        store.removeCookies("example.com")
        assertNull(store.getCookieHeader("example.com"))

        val reloaded = DesktopCookieStore(file)
        assertNull(reloaded.getCookieHeader("example.com"))
    }
}
