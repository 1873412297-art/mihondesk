package mihon.sync.server

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteChangesetStore private constructor(
    private val connection: Connection,
) : ChangesetStore {

    init {
        initSchema()
    }

    private fun initSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS changeset(
                    device_id TEXT NOT NULL,
                    cursor INTEGER NOT NULL,
                    produced_at INTEGER NOT NULL,
                    payload BLOB NOT NULL,
                    received_at INTEGER NOT NULL,
                    PRIMARY KEY(device_id, cursor)
                );
                """.trimIndent(),
            )
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_changeset_received ON changeset(received_at);
                """.trimIndent(),
            )
        }
    }

    @Synchronized
    override fun insertOrReplace(record: ChangesetRecord) {
        val sql = """
            INSERT OR REPLACE INTO changeset(device_id, cursor, produced_at, payload, received_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.deviceId)
            stmt.setLong(2, record.cursor)
            stmt.setLong(3, record.producedAt)
            stmt.setBytes(4, record.payload)
            stmt.setLong(5, record.receivedAt)
            stmt.executeUpdate()
        }
    }

    @Synchronized
    override fun getChangesets(
        sinceCursors: Map<String, Long>,
        excludeDeviceId: String,
    ): List<ChangesetRecord> {
        val sql = """
            SELECT device_id, cursor, produced_at, payload, received_at
            FROM changeset
            WHERE device_id != ?
            ORDER BY device_id ASC, cursor ASC
        """.trimIndent()

        val results = mutableListOf<ChangesetRecord>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, excludeDeviceId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val deviceId = rs.getString(1)
                    val cursor = rs.getLong(2)
                    val producedAt = rs.getLong(3)
                    val payload = rs.getBytes(4)
                    val receivedAt = rs.getLong(5)

                    val sinceCursor = sinceCursors[deviceId] ?: 0L
                    if (cursor > sinceCursor) {
                        results.add(
                            ChangesetRecord(
                                deviceId = deviceId,
                                cursor = cursor,
                                producedAt = producedAt,
                                payload = payload,
                                receivedAt = receivedAt,
                            ),
                        )
                    }
                }
            }
        }
        return results
    }

    @Synchronized
    override fun getHeadCursor(excludeDeviceId: String): Long {
        val sql = "SELECT MAX(cursor) FROM changeset WHERE device_id != ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, excludeDeviceId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val max = rs.getLong(1)
                    if (!rs.wasNull()) {
                        return max
                    }
                }
            }
        }
        return 0L
    }

    @Synchronized
    override fun cleanup(retentionDays: Int, maxChangesets: Int): Int {
        var deletedCount = 0
        if (retentionDays > 0) {
            val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 86_400_000L
            val sql = "DELETE FROM changeset WHERE received_at < ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, cutoff)
                deletedCount += stmt.executeUpdate()
            }
        }

        if (maxChangesets > 0) {
            val countSql = "SELECT count(*) FROM changeset"
            val totalCount = connection.createStatement().use { stmt ->
                stmt.executeQuery(countSql).use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }

            val excess = totalCount - maxChangesets
            if (excess > 0) {
                val deleteExcessSql = """
                    DELETE FROM changeset
                    WHERE (device_id, cursor) IN (
                        SELECT device_id, cursor FROM changeset
                        ORDER BY received_at ASC
                        LIMIT ?
                    )
                """.trimIndent()
                connection.prepareStatement(deleteExcessSql).use { stmt ->
                    stmt.setInt(1, excess)
                    deletedCount += stmt.executeUpdate()
                }
            }
        }

        return deletedCount
    }

    @Synchronized
    override fun close() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    companion object {
        fun open(dbPath: Path): SqliteChangesetStore {
            val parent = dbPath.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val conn = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = WAL;")
                stmt.execute("PRAGMA synchronous = NORMAL;")
                stmt.execute("PRAGMA busy_timeout = 5000;")
            }
            return SqliteChangesetStore(conn)
        }

        fun inMemory(): SqliteChangesetStore {
            val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA synchronous = NORMAL;")
            }
            return SqliteChangesetStore(conn)
        }
    }
}
