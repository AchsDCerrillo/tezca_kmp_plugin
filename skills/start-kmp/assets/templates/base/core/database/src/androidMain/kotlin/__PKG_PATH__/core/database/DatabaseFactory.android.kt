package __PKG__.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

/* sdk:internal */ public actual class DatabaseFactory(@PublishedApi internal val context: Context)

/* sdk:internal */ public actual inline fun <reified T : RoomDatabase> DatabaseFactory.create(
    name: String,
    noinline factory: () -> T,
): RoomDatabase.Builder<T> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<T>(
        context = appContext,
        name = appContext.getDatabasePath(name).absolutePath,
        factory = factory,
    )
}
