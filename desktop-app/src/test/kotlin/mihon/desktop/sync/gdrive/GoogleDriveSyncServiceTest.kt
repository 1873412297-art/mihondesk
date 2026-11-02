package mihon.desktop.sync.gdrive

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveSnapshotStore
import mihon.sync.transport.gdrive.DriveTokenProvider
import mihon.sync.transport.gdrive.GoogleDriveTransport
import mihon.sync.transport.gdrive.GoogleTokens
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.UnknownHostException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

private class MemStore : DriveSnapshotStore {
    var bytes: ByteArray? = null
    var fail: Exception? = null
    val reads = AtomicInteger()
    override suspend fun read(): ByteArray? {
        reads.incrementAndGet()
        fail?.let { throw it }
        return bytes
    }
    override suspend fun write(data: ByteArray) {
        fail?.let { throw it }
        bytes = data
    }
}

/** Mirrors DriveRestSnapshotStore: every request needs an access token. */
private class AuthedStore(private val inner: MemStore, private val tokens: DriveTokenProvider) : DriveSnapshotStore {
    override suspend fun read(): ByteArray? {
        tokens.accessToken()
        return inner.read()
    }
    override suspend fun write(data: ByteArray) {
        tokens.accessToken()
        inner.write(data)
    }
}

private class Repo(var mangas: List<AndroidBackupManga>) : SyncLocalRepository {
    val applied = mutableListOf<Changeset>()
    override suspend fun exportDelta(sinceCursor: Long) = EntityDelta(
        mangas = mangas.filter {
            it.lastModifiedAt >
                sinceCursor
        },
    )
    override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult {
        applied += changeset
        return SyncApplyResult(mangaInserted = changeset.upserts.mangas.size)
    }
}

class GoogleDriveSyncServiceTest {
    @TempDir lateinit var dir: Path
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val now = 1_000_000L

    @AfterEach fun tearDown() = scope.cancel()

    private object Plain : TokenCipher {
        override fun protect(plain: ByteArray) = plain
        override fun unprotect(data: ByteArray) = data
    }

    private fun service(
        name: String,
        store: MemStore,
        repo: Repo,
        secret: String? = "s",
        authorizer: suspend () -> GoogleTokens = { GoogleTokens("a", "r", now + 3_600_000) },
        refresher: suspend (GoogleTokens) -> GoogleTokens = {
            it.copy(
                accessToken = "a2",
                expiresAtMillis =
                now + 3_600_000,
            )
        },
        debounce: Long = 4_000,
    ) = GoogleDriveSyncService(
        config = GoogleDriveConfig("cid", secret),
        vault = DriveTokenVault(dir.resolve("$name/tokens.bin"), Plain),
        stateStore = FileDriveSyncStateStore(dir.resolve("$name/state.json")) { name },
        localRepository = { repo },
        scope = scope,
        authorizer = authorizer,
        refresher = refresher,
        transportFactory = { provider -> GoogleDriveTransport(AuthedStore(store, provider)) },
        clock = { now },
        debounceMillis = debounce,
    )

    @Test
    fun `readiness follows sign-in and the dirty check uses the Drive watermark`(): Unit = runBlocking {
        val manga = AndroidBackupManga(source = 1L, url = "/a", title = "A", favorite = true, lastModifiedAt = 500L)
        val repo = Repo(listOf(manga))
        val s = service("w", MemStore(), repo)
        s.isReady() shouldBe false
        s.hasLocalChanges() shouldBe false
        s.signIn() shouldBe true
        s.isReady() shouldBe true
        s.hasLocalChanges() shouldBe true
        s.syncNow().shouldNotBeNull().success shouldBe true
        s.hasLocalChanges() shouldBe false
        repo.mangas = repo.mangas +
            AndroidBackupManga(source = 1L, url = "/b", title = "B", favorite = true, lastModifiedAt = now + 10)
        s.hasLocalChanges() shouldBe true
    }

    @Test
    fun `not configured without secret`(): Unit = runBlocking {
        val s = service("d", MemStore(), Repo(emptyList()), secret = null)
        s.state.value.configured shouldBe false
        s.state.value.error shouldBe GoogleDriveError.NOT_CONFIGURED
        s.signIn() shouldBe false
        s.syncNow() shouldBe null
    }

    @Test
    fun `desktop pushes and second device pulls the shelf through the same snapshot`(): Unit = runBlocking {
        val store = MemStore()
        val desktop =
            service(
                "desktop",
                store,
                Repo(listOf(AndroidBackupManga(source = 1, url = "/m", title = "M", lastModifiedAt = 10))),
            )
        desktop.syncNow() shouldBe null // not signed in yet
        desktop.signIn() shouldBe true
        desktop.state.value.signedIn shouldBe true
        val report = desktop.syncNow().shouldNotBeNull()
        report.success shouldBe true
        report.pushedChangeset.shouldNotBeNull()
        desktop.state.value.lastSyncAt shouldBe now

        val phoneRepo = Repo(emptyList())
        val phone = service("phone", store, phoneRepo)
        phone.signIn()
        phone.syncNow()!!.pulledChangesetCount shouldBe 1
        phoneRepo.applied.single().upserts.mangas.single().url shouldBe "/m"
        phone.syncNow()!!.pulledChangesetCount shouldBe 0
    }

    @Test
    fun `expired access token is refreshed and persisted`(): Unit = runBlocking {
        var refreshed = 0
        val s = service(
            "d",
            MemStore(),
            Repo(emptyList()),
            authorizer = { GoogleTokens("old", "r", 0) },
            refresher = {
                refreshed++
                GoogleTokens("new", it.refreshToken, now + 3_600_000)
            },
        )
        s.signIn()
        s.syncNow()!!.success shouldBe true
        refreshed shouldBe 1
        DriveTokenVault(dir.resolve("d/tokens.bin"), Plain).load()!!.accessToken shouldBe "new"
    }

    @Test
    fun `revoked refresh token signs out with auth error`(): Unit = runBlocking {
        val s = service(
            "d",
            MemStore(),
            Repo(emptyList()),
            authorizer = { GoogleTokens("old", "r", 0) },
            refresher = { throw DriveAuthException("invalid_grant") },
        )
        s.signIn()
        s.syncNow()!!.success shouldBe false
        s.state.value.error shouldBe GoogleDriveError.AUTH_EXPIRED
        s.state.value.signedIn shouldBe false
    }

    @Test
    fun `network failure keeps session and reports network error`(): Unit = runBlocking {
        val store = MemStore().apply { fail = UnknownHostException("www.googleapis.com") }
        val s = service("d", store, Repo(emptyList()))
        s.signIn()
        s.syncNow()!!.success shouldBe false
        s.state.value.error shouldBe GoogleDriveError.NETWORK
        s.state.value.signedIn shouldBe true
    }

    @Test
    fun `cancelled browser consent reports auth cancelled`(): Unit = runBlocking {
        val s =
            service("d", MemStore(), Repo(emptyList()), authorizer = { throw OAuthCancelledException("access_denied") })
        s.signIn() shouldBe false
        s.state.value.error shouldBe GoogleDriveError.AUTH_CANCELLED
        s.state.value.signedIn shouldBe false
    }

    @Test
    fun `requestSync debounces bursts into one sync`(): Unit = runBlocking {
        val store = MemStore()
        val s = service("d", store, Repo(emptyList()), debounce = 150)
        s.signIn()
        repeat(5) {
            s.requestSync()
            delay(20)
        }
        delay(600)
        // one sync = one pull read + one head/verify-free path; never 5 separate syncs
        (store.reads.get() in 1..2) shouldBe true
        s.state.value.lastSyncAt shouldBe now
    }

    @Test
    fun `sign out clears tokens and sync state`(): Unit = runBlocking {
        val s = service("d", MemStore(), Repo(listOf(AndroidBackupManga(source = 1, url = "/m", lastModifiedAt = 5))))
        s.signIn()
        s.syncNow()
        s.signOut()
        s.state.value.signedIn shouldBe false
        s.state.value.lastSyncAt shouldBe 0L
        DriveTokenVault(dir.resolve("d/tokens.bin"), Plain).load() shouldBe null
        FileDriveSyncStateStore(dir.resolve("d/state.json")) { "d" }.getLastPushWatermark() shouldBe 0L
    }
}
