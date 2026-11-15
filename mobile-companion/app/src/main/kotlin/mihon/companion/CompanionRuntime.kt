package mihon.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import mihon.companion.data.FileSyncStateStore
import mihon.companion.data.LibraryStore
import mihon.companion.mihonfolder.MihonAutoImporter
import mihon.companion.mihonfolder.SafMihonBackupSource
import mihon.companion.sync.AuthRetryingSnapshotStore
import mihon.companion.sync.CompanionSyncService
import mihon.companion.sync.DriveAuthorizer
import mihon.companion.sync.DriveTokenSource
import mihon.companion.sync.LibraryRefresher
import mihon.companion.sync.SyncScheduler
import mihon.sync.transport.gdrive.DriveRestSnapshotStore
import mihon.sync.transport.gdrive.GoogleDriveTransport
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Composition root: wires storage, the Mihon folder, Google authorization and the shared Drive transport. */
class CompanionRuntime(
    val library: LibraryStore,
    val sync: CompanionSyncService,
    val authorizer: DriveAuthorizer,
    val scheduler: SyncScheduler,
    val mihonFolder: MihonAutoImporter,
    val mihonSource: SafMihonBackupSource,
    val refresher: LibraryRefresher,
) {
    companion object {
        fun create(context: Context): CompanionRuntime {
            val dir = File(context.filesDir, "library")
            val http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val library = LibraryStore(File(dir, "library.pb"), Dispatchers.IO)
            val authorizer = DriveAuthorizer(context.applicationContext)
            val tokens = DriveTokenSource(authorizer)
            val sync = CompanionSyncService(
                library = library,
                stateStore = FileSyncStateStore(File(dir, "sync-state.json")),
                tokens = tokens,
                transportFactory = {
                    val drive = DriveRestSnapshotStore(http, tokens::accessToken)
                    GoogleDriveTransport(AuthRetryingSnapshotStore(drive, tokens::invalidate))
                },
            )
            val mihonSource = SafMihonBackupSource(context.applicationContext)
            val mihonFolder = MihonAutoImporter(
                store = library,
                source = mihonSource,
                stateFile = File(dir, "mihon-folder.json"),
                deviceId = sync::deviceId,
                io = Dispatchers.IO,
            )
            return CompanionRuntime(
                library = library,
                sync = sync,
                authorizer = authorizer,
                scheduler = SyncScheduler(context.applicationContext),
                mihonFolder = mihonFolder,
                mihonSource = mihonSource,
                refresher = LibraryRefresher(mihonFolder, sync),
            )
        }
    }
}
