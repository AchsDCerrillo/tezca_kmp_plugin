package __PKG__.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import __PKG__.core.datastore.DATASTORE_FILE_NAME
import __PKG__.core.datastore.createDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual val platformDataStoreModule: Module = module {
    single<DataStore<Preferences>> {
        createDataStore {
            val documents = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            requireNotNull(documents?.path) + "/$DATASTORE_FILE_NAME"
        }
    }
}
