package uz.donfinity.itubepro.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.core.player.VideoQuality

/**
 * Quality picker. "Auto" lets ExoPlayer adapt to bandwidth; a manual pick pins one quality.
 *
 * @param playingHeight the height being shown right now, so "Auto" can show what it picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    qualities: List<VideoQuality>,
    selected: VideoQuality?,
    playingHeight: Int,
    onSelect: (VideoQuality?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.quality_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        QualityRow(
            label = stringResource(R.string.quality_auto),
            supporting = if (selected == null && playingHeight > 0) {
                stringResource(R.string.quality_auto_current, "${playingHeight}p")
            } else {
                null
            },
            checked = selected == null,
            onClick = { onSelect(null) },
        )
        // Highest first, like YouTube.
        qualities.asReversed().forEach { quality ->
            QualityRow(
                label = quality.label,
                supporting = null,
                checked = quality == selected,
                onClick = { onSelect(quality) },
            )
        }
    }
}

@Composable
private fun QualityRow(label: String, supporting: String?, checked: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (checked) {
            { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.quality_selected)) }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Short label for a quality button: "Auto (720p)" or "1080p". */
@Composable
fun qualityButtonLabel(selected: VideoQuality?, playingHeight: Int): String = when {
    selected != null -> selected.label
    playingHeight > 0 -> stringResource(R.string.quality_auto_short, "${playingHeight}p")
    else -> stringResource(R.string.quality_auto)
}
