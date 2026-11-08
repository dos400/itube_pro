package uz.donfinity.itubepro.ui.update

import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.AppInfo
import uz.donfinity.itubepro.domain.model.AppRelease
import uz.donfinity.itubepro.domain.usecase.CheckForUpdateUseCase
import uz.donfinity.itubepro.system.ApkInstaller
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface UpdateState {
    /** Local build (no update repo), so there's nothing to check. */
    data object Disabled : UpdateState
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    /** Session committed; the system's install dialog is (about to be) on screen. */
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val release: AppRelease?, @StringRes val message: Int) : UpdateState
}

sealed interface UpdateEvent {
    /** Found by the automatic check at app start: announce it once (snackbar). */
    data class Found(val release: AppRelease) : UpdateEvent
    /** "Install unknown apps" isn't allowed yet: open that system screen. */
    data object NeedsInstallPermission : UpdateEvent
    data class Message(@StringRes val message: Int) : UpdateEvent
}

/**
 * Created once by AppNavHost (Activity scope) and passed to Settings, so the automatic check
 * runs once per app start and both places show the same state.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val installer: ApkInstaller,
    appInfo: AppInfo,
) : ViewModel() {

    val currentVersionName: String = appInfo.versionName

    private val _state = MutableStateFlow<UpdateState>(
        if (appInfo.updateRepo.isBlank()) UpdateState.Disabled else UpdateState.Idle,
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events: Flow<UpdateEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    init {
        if (_state.value != UpdateState.Disabled) check(automatic = true)
        viewModelScope.launch {
            installer.results.collect { result ->
                val release = (_state.value as? UpdateState.Installing)?.release ?: return@collect
                _state.value = when (result) {
                    ApkInstaller.Result.Cancelled -> UpdateState.Available(release)
                    is ApkInstaller.Result.Failed -> {
                        Log.w(TAG, "Install failed: ${result.message}")
                        _events.trySend(UpdateEvent.Message(R.string.update_install_failed))
                        UpdateState.Failed(release, R.string.update_install_failed)
                    }
                }
            }
        }
    }

    /** One entry point for the Settings row: what a tap does depends on the state. */
    fun onClick() {
        when (val current = _state.value) {
            UpdateState.Disabled, UpdateState.Checking, is UpdateState.Downloading -> Unit
            UpdateState.Idle, UpdateState.UpToDate -> check(automatic = false)
            // Installing: the system dialog was dismissed in a way we weren't told about. Retry.
            is UpdateState.Available, is UpdateState.Installing -> install()
            is UpdateState.Failed -> if (current.release != null) install() else check(automatic = false)
        }
    }

    fun install() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Installing -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return
        if (job?.isActive == true) return
        if (!installer.canInstall()) {
            _events.trySend(UpdateEvent.NeedsInstallPermission)
            return
        }
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                installer.install(release.apkUrl, release.apkSizeBytes) { progress ->
                    _state.value = UpdateState.Downloading(release, progress)
                }
                _state.value = UpdateState.Installing(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                _events.trySend(UpdateEvent.Message(R.string.update_download_failed))
                _state.value = UpdateState.Failed(release, R.string.update_download_failed)
            }
        }
    }

    fun installPermissionIntent(): Intent = installer.installPermissionIntent()

    private fun check(automatic: Boolean) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val release = checkForUpdate()
                if (release == null) {
                    UpdateState.UpToDate
                } else {
                    if (automatic) _events.trySend(UpdateEvent.Found(release))
                    UpdateState.Available(release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                // The automatic check stays quiet: no network at app start isn't worth a message.
                if (automatic) UpdateState.Idle
                else UpdateState.Failed(null, (e as? AppException)?.messageRes() ?: R.string.error_unknown)
            }
        }
    }

    private companion object {
        const val TAG = "UpdateViewModel"
    }
}
