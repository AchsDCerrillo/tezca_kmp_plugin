package __PKG__.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import __PKG__.core.database.sync.SyncMetadataDao
import __PKG__.core.database.sync.SyncMetadataEntity

/** App-wide database. Holds only cross-feature bookkeeping; features keep their own databases. */
@Database(entities = [SyncMetadataEntity::class], version = 1)
@ConstructedBy(CoreDatabaseConstructor::class)
/* sdk:internal */ public abstract class CoreDatabase : RoomDatabase() {
    public abstract fun syncMetadataDao(): SyncMetadataDao
}

// The Room compiler generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
/* sdk:internal */ public expect object CoreDatabaseConstructor : RoomDatabaseConstructor<CoreDatabase> {
    override fun initialize(): CoreDatabase
}
