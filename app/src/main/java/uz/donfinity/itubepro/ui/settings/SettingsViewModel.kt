package uz.donfinity.itubepro.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.model.SegmentCategory
import uz.donfinity.itubepro.domain.repository.SettingsRepository
import uz.donfinity.itubepro.system.BatteryOptimization

/**
 * Talks to SettingsRepository directly, with no use case in between. A use case that only
 * forwards get/set adds no logic, just ceremony. Use cases are for actual rules (like
 * StreamSelector).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val batteryOptimization: BatteryOptimization,
) : ViewModel() {

    val audioOnly: StateFlow<Boolean> =
        settings.audioOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoplayNext: StateFlow<Boolean> =
        settings.autoplayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onAutoplayNextChange(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoplayNext(enabled) }
    }

    val sponsorBlockEnabled: StateFlow<Boolean> =
        settings.sponsorBlockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sponsorBlockCategories: StateFlow<Set<SegmentCategory>> =
        settings.sponsorBlockCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SegmentCategory.DEFAULTS)

    fun onSponsorBlockEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settings.setSponsorBlockEnabled(enabled) }
    }

    fun onSponsorBlockCategoryToggle(category: SegmentCategory) {
        val current = sponsorBlockCategories.value
        val updated = if (category in current) current - category else current + category
        viewModelScope.launch { settings.setSponsorBlockCategories(updated) }
    }

    private val _batteryUnrestricted = MutableStateFlow(batteryOptimization.isIgnoringOptimizations())
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    fun onAudioOnlyChange(enabled: Boolean) {
        viewModelScope.launch { settings.setAudioOnly(enabled) }
    }

    /**
     * The user may change the exemption in system settings, so re-check every time the screen
     * resumes. The system doesn't notify us of the change.
     */
    fun onResume() {
        _batteryUnrestricted.value = batteryOptimization.isIgnoringOptimizations()
    }

    /** Already exempt → open the list (to undo); otherwise ask with the system dialog. */
    fun batteryIntent(): Intent =
        if (_batteryUnrestricted.value) batteryOptimization.settingsIntent() else batteryOptimization.requestExemptionIntent()

    fun batteryFallbackIntent(): Intent = batteryOptimization.settingsIntent()
}
