package __PKG__.core.database.sync

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
/* sdk:internal */ public interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    public suspend fun get(key: String): SyncMetadataEntity?

    // REPLACE instead of @Upsert: @Upsert relies on the SQLite error message to detect conflicts,
    // which the Android stubs used by JVM host tests drop.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun upsert(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    public suspend fun delete(key: String)

    @Query("DELETE FROM sync_metadata")
    public suspend fun clear()
}
