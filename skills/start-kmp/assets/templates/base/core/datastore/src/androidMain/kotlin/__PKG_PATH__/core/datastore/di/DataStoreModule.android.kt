package __PKG__.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import __PKG__.core.datastore.DATASTORE_FILE_NAME
import __PKG__.core.datastore.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataStoreModule: Module = module {
    single<DataStore<Preferences>> {
        val context = androidContext()
        createDataStore { context.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath }
    }
}
