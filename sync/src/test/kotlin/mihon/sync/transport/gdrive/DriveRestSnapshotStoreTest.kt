package mihon.sync.transport.gdrive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test

private class FakeDrive(val handler: (Request, String) -> Pair<Int, String>) : Interceptor {
    val requests = mutableListOf<Pair<Request, String>>()
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        requests += req to body
        val (code, resp) = handler(req, body)
        return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(resp.toResponseBody("application/json".toMediaType())).build()
    }
}

class DriveRestSnapshotStoreTest {
    private fun store(fake: FakeDrive) = DriveRestSnapshotStore(
        client = OkHttpClient.Builder().addInterceptor(fake).build(),
        accessToken = { "tok" },
    )

    @Test
    fun `read returns null when no snapshot in appDataFolder`(): Unit = runBlocking {
        val fake = FakeDrive { _, _ -> 200 to """{"files":[]}""" }
        store(fake).read() shouldBe null
        val listUrl = fake.requests.single().first.url
        listUrl.queryParameter("spaces") shouldBe "appDataFolder"
        listUrl.queryParameter("q")!! shouldContain DriveRestSnapshotStore.FILE_NAME
        fake.requests.single().first.header("Authorization") shouldBe "Bearer tok"
    }

    @Test
    fun `write creates file in appDataFolder when missing, then updates by id`(): Unit = runBlocking {
        var created = false
        val fake = FakeDrive { req, _ ->
            when {
                req.method == "GET" && req.url.encodedPath == "/drive/v3/files" ->
                    200 to (if (created) """{"files":[{"id":"F1"}]}""" else """{"files":[]}""")
                req.method == "POST" -> {
                    created = true
                    200 to """{"id":"F1"}"""
                }
                req.method == "PATCH" -> 200 to """{"id":"F1"}"""
                req.method == "GET" -> 200 to "payload"
                else -> 500 to ""
            }
        }
        val s = store(fake)
        s.write("one".toByteArray())
        val post = fake.requests.first { it.first.method == "POST" }
        post.first.url.queryParameter("uploadType") shouldBe "multipart"
        post.second shouldContain "\"parents\":[\"appDataFolder\"]"
        s.write("two".toByteArray())
        fake.requests.last().first.method shouldBe "PATCH"
        fake.requests.last().first.url.encodedPath shouldBe "/upload/drive/v3/files/F1"
        String(s.read()!!) shouldBe "payload"
    }

    @Test
    fun `401 surfaces as auth exception`() {
        val fake = FakeDrive { _, _ -> 401 to "{}" }
        shouldThrow<DriveAuthException> { runBlocking { store(fake).read() } }
    }
}
