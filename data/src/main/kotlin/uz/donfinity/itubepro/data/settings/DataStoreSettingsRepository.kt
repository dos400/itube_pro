package uz.donfinity.itubepro.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uz.donfinity.itubepro.domain.repository.SettingsRepository

/**
 * DataStore replaces SharedPreferences. Reads are a Flow (no blocking main-thread disk
 * reads), and writes are transactional suspend calls, safe to run from any coroutine.
 */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val audioOnly: Flow<Boolean> =
        dataStore.data.map { it[KEY_AUDIO_ONLY] ?: false }.distinctUntilChanged()

    override suspend fun setAudioOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_AUDIO_ONLY] = enabled }
    }

    private companion object {
        val KEY_AUDIO_ONLY = booleanPreferencesKey("audio_only")
    }
}
