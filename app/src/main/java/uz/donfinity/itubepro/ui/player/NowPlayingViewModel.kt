package uz.donfinity.itubepro.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uz.donfinity.itubepro.core.player.NowPlayingState
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.ui.common.messageRes

/** Backs the app-wide mini-player, PiP video and the playback error snackbar. */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val state: StateFlow<NowPlayingState> = playerConnection.state

    /** The MediaController, for binding a video surface. */
    val player: StateFlow<Player?> = playerConnection.controller

    /**
     * Playback progress (0..1) for the mini-player's progress line. The player has no
     * "position changed" event (position changes continuously), so we sample it twice a
     * second, but only while someone is collecting.
     */
    val progress: StateFlow<Float?> = flow {
        while (true) {
            emit(playerConnection.progress())
            delay(PROGRESS_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    /** One-off events (string resource IDs): collect once and show as a snackbar. */
    val errorMessages: Flow<Int> = playerConnection.errors.map { it.messageRes() }

    fun onPlayPause() = playerConnection.togglePlayPause()

    fun onClose() = playerConnection.stop()

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
