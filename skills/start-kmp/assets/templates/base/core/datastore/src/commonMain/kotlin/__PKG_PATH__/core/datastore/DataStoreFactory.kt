package __PKG__.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

internal const val DATASTORE_FILE_NAME: String = "app.preferences_pb"

/**
 * DataStore must be a single instance per file, so it is only created here and provided as a
 * Koin `single`. Feature preference data sources inject `DataStore<Preferences>`.
 */
/* sdk:internal */ public fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })
