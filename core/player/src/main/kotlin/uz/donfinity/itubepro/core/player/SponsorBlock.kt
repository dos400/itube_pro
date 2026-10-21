package uz.donfinity.itubepro.core.player

import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.session.SessionCommand
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.model.SegmentCategory
import uz.donfinity.itubepro.domain.model.SkipSegment
import uz.donfinity.itubepro.domain.model.SkipSegments
import uz.donfinity.itubepro.domain.repository.SettingsRepository
import uz.donfinity.itubepro.domain.repository.SponsorBlockRepository

/**
 * Skips SponsorBlock segments, inside the service so it also works with the screen off.
 *
 * ref/Tubular checks the position on a timer and seeks when it's inside a segment
 * (player/Player.java, triggerCheckForSponsorBlockSegments), which needs extra state to avoid
 * skip loops. We use ExoPlayer [PlayerMessage]s instead: the player calls us back exactly
 * when playback *reaches* a segment start. No polling, no timing drift, and seeking manually
 * into the middle of a segment doesn't trigger a skip (the user meant to go there).
 */
@OptIn(UnstableApi::class)
internal class SponsorBlockSkipper(
    private val player: ExoPlayer,
    private val repository: SponsorBlockRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    /** Current item's (merged) segments changed; publish them to the UI. */
    private val onSegmentsChanged: (mediaId: String?, segments: List<SkipSegment>) -> Unit,
    /** A segment was skipped; tell the UI (snackbar with Undo). */
    private val onSkipped: (SkipSegment) -> Unit,
) : Player.Listener {

    private var enabled = false
    private var categories: Set<SegmentCategory> = emptySet()
    private var loadJob: Job? = null
    private var messages: List<PlayerMessage> = emptyList()

    /** Start of a segment the user un-skipped: play it this time. */
    private var allowedStartMs: Long? = null

    fun start() {
        player.addListener(this)
        scope.launch {
            combine(settings.sponsorBlockEnabled, settings.sponsorBlockCategories, ::Pair).collect { (on, cats) ->
                enabled = on
                categories = cats
                reload()
            }
        }
    }

    /** Also fires when PlaybackRecovery replaces the item after a 403 (same video, new tag). */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) allowedStartMs = null
        reload()
    }

    /** Seek back to [startMs] and let that segment play once. Called from the UI's "Undo". */
    fun undo(startMs: Long) {
        allowedStartMs = startMs
        player.seekTo(startMs)
    }

    private fun reload() {
        loadJob?.cancel()
        clearMessages()
        val item = player.currentMediaItem
        onSegmentsChanged(item?.mediaId, emptyList())
        if (!enabled || item == null || categories.isEmpty()) return
        // Live streams have no fixed timeline to skip in.
        if ((item.localConfiguration?.tag as? PlaybackSource)?.liveHls != null) return

        val wanted = categories
        loadJob = scope.launch {
            val segments = try {
                SkipSegments.merge(repository.segments(item.mediaId, wanted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SponsorBlock is optional: play normally when it's unreachable.
                Log.w(TAG, "Couldn't load segments for ${item.mediaId}", e)
                emptyList()
            }
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            schedule(segments)
            onSegmentsChanged(item.mediaId, segments)
        }
    }

    private fun schedule(segments: List<SkipSegment>) {
        clearMessages()
        val index = player.currentMediaItemIndex
        messages = segments.map { segment ->
            player.createMessage { _, _ -> onSegmentReached(segment) }
                // Deliver on the main thread (the player's application looper), not the playback thread.
                .setLooper(Looper.getMainLooper())
                .setPosition(index, segment.startMs)
                // Keep the message: after seeking back before the segment it fires again.
                .setDeleteAfterDelivery(false)
                .send()
        }
    }

    private fun onSegmentReached(segment: SkipSegment) {
        if (!enabled || allowedStartMs == segment.startMs) return
        Log.i(TAG, "Skipping ${segment.category} ${segment.startMs}→${segment.endMs}ms")
        player.seekTo(segment.endMs)
        onSkipped(segment)
    }

    private fun clearMessages() {
        messages.forEach { it.cancel() }
        messages = emptyList()
    }

    private companion object {
        const val TAG = "SponsorBlock"
    }
}

/**
 * How SponsorBlock data travels between the service and controllers (the UI):
 * - the current segment list is *state* → session extras, which controllers can read even
 *   if they connect later;
 * - "a segment was skipped" is an *event* → a broadcast custom command;
 * - "Undo" is a request from the UI → a custom command to the session.
 */
internal object SponsorBlockProtocol {
    const val ACTION_SKIPPED = "uz.donfinity.itubepro.SPONSORBLOCK_SKIPPED"
    const val ACTION_UNDO = "uz.donfinity.itubepro.SPONSORBLOCK_UNDO"

    val SKIPPED_COMMAND = SessionCommand(ACTION_SKIPPED, Bundle.EMPTY)
    val UNDO_COMMAND = SessionCommand(ACTION_UNDO, Bundle.EMPTY)

    private const val KEY_MEDIA_ID = "sb_media_id"
    private const val KEY_STARTS = "sb_starts"
    private const val KEY_ENDS = "sb_ends"
    private const val KEY_CATEGORIES = "sb_categories"
    private const val KEY_START = "sb_start"
    private const val KEY_END = "sb_end"
    private const val KEY_CATEGORY = "sb_category"

    fun segmentsToBundle(mediaId: String?, segments: List<SkipSegment>) = Bundle().apply {
        putString(KEY_MEDIA_ID, mediaId)
        putLongArray(KEY_STARTS, segments.map { it.startMs }.toLongArray())
        putLongArray(KEY_ENDS, segments.map { it.endMs }.toLongArray())
        putStringArray(KEY_CATEGORIES, segments.map { it.category.apiName }.toTypedArray())
    }

    fun segmentsFromBundle(bundle: Bundle): Pair<String?, List<SkipSegment>> {
        val starts = bundle.getLongArray(KEY_STARTS) ?: return null to emptyList()
        val ends = bundle.getLongArray(KEY_ENDS) ?: return null to emptyList()
        val cats = bundle.getStringArray(KEY_CATEGORIES) ?: return null to emptyList()
        val segments = starts.indices.mapNotNull { i ->
            val category = cats.getOrNull(i)?.let(SegmentCategory::fromApiName) ?: return@mapNotNull null
            SkipSegment(starts[i], ends.getOrElse(i) { starts[i] }, category)
        }
        return bundle.getString(KEY_MEDIA_ID) to segments
    }

    fun segmentToBundle(segment: SkipSegment) = Bundle().apply {
        putLong(KEY_START, segment.startMs)
        putLong(KEY_END, segment.endMs)
        putString(KEY_CATEGORY, segment.category.apiName)
    }

    fun segmentFromBundle(bundle: Bundle): SkipSegment? {
        val category = bundle.getString(KEY_CATEGORY)?.let(SegmentCategory::fromApiName) ?: return null
        return SkipSegment(bundle.getLong(KEY_START), bundle.getLong(KEY_END), category)
    }
}
