package uz.donfinity.itubepro.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uz.donfinity.itubepro.domain.model.SegmentCategory
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

    override val autoplayNext: Flow<Boolean> =
        dataStore.data.map { it[KEY_AUTOPLAY_NEXT] ?: true }.distinctUntilChanged()

    override suspend fun setAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOPLAY_NEXT] = enabled }
    }

    override val sponsorBlockEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_SPONSORBLOCK_ENABLED] ?: false }.distinctUntilChanged()

    override suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPONSORBLOCK_ENABLED] = enabled }
    }

    /** Stored by API name, so renaming an enum constant later can't corrupt saved settings. */
    override val sponsorBlockCategories: Flow<Set<SegmentCategory>> = dataStore.data
        .map { prefs ->
            prefs[KEY_SPONSORBLOCK_CATEGORIES]?.mapNotNull(SegmentCategory::fromApiName)?.toSet()
                ?: SegmentCategory.DEFAULTS
        }
        .distinctUntilChanged()

    override suspend fun setSponsorBlockCategories(categories: Set<SegmentCategory>) {
        dataStore.edit { prefs -> prefs[KEY_SPONSORBLOCK_CATEGORIES] = categories.map { it.apiName }.toSet() }
    }

    private companion object {
        val KEY_AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val KEY_SPONSORBLOCK_ENABLED = booleanPreferencesKey("sponsorblock_enabled")
        val KEY_SPONSORBLOCK_CATEGORIES = stringSetPreferencesKey("sponsorblock_categories")
    }
}
