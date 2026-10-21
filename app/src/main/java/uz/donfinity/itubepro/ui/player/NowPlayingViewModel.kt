package uz.donfinity.itubepro.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import uz.donfinity.itubepro.core.player.NowPlayingState
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.ui.common.messageRes

/** Backs the app-wide "now playing" bar and the playback error snackbar. */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val state: StateFlow<NowPlayingState> = playerConnection.state

    /** One-off events (string resource IDs): collect once and show as a snackbar. */
    val errorMessages: Flow<Int> = playerConnection.errors.map { it.messageRes() }

    fun onPlayPause() = playerConnection.togglePlayPause()

    fun onClose() = playerConnection.stop()
}
