package __PKG__.core.database.sync

import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Freshness bookkeeping for offline-first repositories: refresh from the network only when the
 * cached copy is older than its time-to-live, or when the caller forces it.
 */
/* sdk:internal */ public class SyncMetadataStore(
    private val dao: SyncMetadataDao,
    private val clock: Clock = Clock.System,
) {
    public suspend fun isStale(key: String, ttl: Duration): Boolean {
        val last = dao.get(key)?.lastSyncedAtEpochMs ?: return true
        return clock.now().toEpochMilliseconds() - last >= ttl.inWholeMilliseconds
    }

    public suspend fun markSynced(key: String) {
        dao.upsert(SyncMetadataEntity(key = key, lastSyncedAtEpochMs = clock.now().toEpochMilliseconds()))
    }

    public suspend fun invalidate(key: String) {
        dao.delete(key)
    }
}
