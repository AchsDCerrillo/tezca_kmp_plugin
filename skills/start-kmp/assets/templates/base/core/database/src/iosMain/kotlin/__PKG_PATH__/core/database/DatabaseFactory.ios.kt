package __PKG__.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/* sdk:internal */ public actual class DatabaseFactory {
    @PublishedApi
    @OptIn(ExperimentalForeignApi::class)
    internal fun documentDirectory(): String {
        val url = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(url?.path) { "Documents directory not available" }
    }
}

/* sdk:internal */ public actual inline fun <reified T : RoomDatabase> DatabaseFactory.create(
    name: String,
    noinline factory: () -> T,
): RoomDatabase.Builder<T> = Room.databaseBuilder<T>(name = "${documentDirectory()}/$name", factory = factory)
