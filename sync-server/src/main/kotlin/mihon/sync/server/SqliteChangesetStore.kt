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
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS device_ownership(
                    device_id TEXT PRIMARY KEY,
                    owner_token_hash TEXT NOT NULL,
                    last_cursor INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }
    }

    @Synchronized
    override fun insertOrReplace(record: ChangesetRecord) {
        require(record.payload.size <= MAX_PAYLOAD_BYTES) {
            "Changeset payload exceeds limit of $MAX_PAYLOAD_BYTES bytes (actual: ${record.payload.size})"
        }
        require(record.deviceId.isNotBlank()) {
            "Changeset deviceId cannot be blank"
        }
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

        val updateOwnershipSql = """
            INSERT INTO device_ownership(device_id, owner_token_hash, last_cursor, created_at, updated_at)
            VALUES (?, '', ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                last_cursor = MAX(device_ownership.last_cursor, excluded.last_cursor),
                updated_at = excluded.updated_at
        """.trimIndent()
        connection.prepareStatement(updateOwnershipSql).use { stmt ->
            stmt.setString(1, record.deviceId)
            stmt.setLong(2, record.cursor)
            stmt.setLong(3, record.receivedAt)
            stmt.setLong(4, record.receivedAt)
            stmt.executeUpdate()
        }
    }

    @Synchronized
    override fun getChangesets(
        sinceCursors: Map<String, Long>,
        excludeDeviceId: String,
    ): List<ChangesetRecord> {
        val filteredSince = sinceCursors.filter { it.key.isNotBlank() && it.value >= 0 }
        val sql = if (filteredSince.isEmpty()) {
            """
                SELECT device_id, cursor, produced_at, payload, received_at
                FROM changeset
                WHERE device_id != ? AND cursor > 0
                ORDER BY device_id ASC, cursor ASC
            """.trimIndent()
        } else {
            val caseClauses = filteredSince.keys.joinToString(" ") { "WHEN ? THEN ?" }
            """
                SELECT device_id, cursor, produced_at, payload, received_at
                FROM changeset
                WHERE device_id != ?
                  AND cursor > (
                      CASE device_id
                          $caseClauses
                          ELSE 0
                      END
                  )
                ORDER BY device_id ASC, cursor ASC
            """.trimIndent()
        }

        val results = mutableListOf<ChangesetRecord>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, excludeDeviceId)
            if (filteredSince.isNotEmpty()) {
                var idx = 2
                for ((device, since) in filteredSince) {
                    stmt.setString(idx++, device)
                    stmt.setLong(idx++, since)
                }
            }
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(
                        ChangesetRecord(
                            deviceId = rs.getString(1),
                            cursor = rs.getLong(2),
                            producedAt = rs.getLong(3),
                            payload = rs.getBytes(4),
                            receivedAt = rs.getLong(5),
                        ),
                    )
                }
            }
        }
        return results
    }

    @Synchronized
    override fun getDeviceOwner(deviceId: String): String? {
        val sql = "SELECT owner_token_hash FROM device_ownership WHERE device_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, deviceId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val hash = rs.getString(1)
                    if (!rs.wasNull() && hash.isNotBlank()) return hash
                }
            }
        }
        return null
    }

    @Synchronized
    override fun bindDeviceOwner(deviceId: String, ownerTokenHash: String) {
        val now = System.currentTimeMillis()
        val sql = """
            INSERT INTO device_ownership(device_id, owner_token_hash, last_cursor, created_at, updated_at)
            VALUES (?, ?, 0, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                owner_token_hash = CASE WHEN device_ownership.owner_token_hash = '' THEN excluded.owner_token_hash ELSE device_ownership.owner_token_hash END,
                updated_at = excluded.updated_at
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, deviceId)
            stmt.setString(2, ownerTokenHash)
            stmt.setLong(3, now)
            stmt.setLong(4, now)
            stmt.executeUpdate()
        }
    }

    @Synchronized
    override fun getLastCursor(deviceId: String): Long? {
        val sql = "SELECT last_cursor FROM device_ownership WHERE device_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, deviceId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val cursor = rs.getLong(1)
                    if (!rs.wasNull() && cursor > 0) return cursor
                }
            }
        }
        // Fallback to max cursor in changeset table if not in device_ownership
        val fallbackSql = "SELECT MAX(cursor) FROM changeset WHERE device_id = ?"
        connection.prepareStatement(fallbackSql).use { stmt ->
            stmt.setString(1, deviceId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val max = rs.getLong(1)
                    if (!rs.wasNull() && max > 0) return max
                }
            }
        }
        return null
    }

    @Synchronized
    override fun getChangeset(deviceId: String, cursor: Long): ChangesetRecord? {
        val sql = """
            SELECT device_id, cursor, produced_at, payload, received_at
            FROM changeset
            WHERE device_id = ? AND cursor = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, deviceId)
            stmt.setLong(2, cursor)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return ChangesetRecord(
                        deviceId = rs.getString(1),
                        cursor = rs.getLong(2),
                        producedAt = rs.getLong(3),
                        payload = rs.getBytes(4),
                        receivedAt = rs.getLong(5),
                    )
                }
            }
        }
        return null
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
        const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024 // 16 MiB

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
