package uz.donfinity.itubepro.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.model.MediaStream
import uz.donfinity.itubepro.ui.theme.ItubeProTheme

/**
 * Stateful entry point: gets the ViewModel and collects its state. Everything below it is
 * stateless, which keeps it previewable and free of logic.
 */
@Composable
fun DebugExtractionScreen(viewModel: DebugExtractionViewModel = hiltViewModel()) {
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val url by viewModel.url.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    DebugExtractionContent(
        url = url,
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onExtractClick = viewModel::onExtractClick,
        onRetryClick = viewModel::onRetryClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugExtractionContent(
    url: String,
    state: DebugUiState,
    onUrlChange: (String) -> Unit,
    onExtractClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.debug_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.debug_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onExtractClick() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = onExtractClick,
                    enabled = state !is DebugUiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.debug_extract)) }
            }

            when (state) {
                DebugUiState.Idle -> Unit
                DebugUiState.Loading -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                }
                is DebugUiState.Error -> item {
                    ErrorBlock(message = stringResource(state.messageRes), onRetryClick = onRetryClick)
                }
                is DebugUiState.Success -> {
                    item { VideoHeader(state.video) }
                    item {
                        Text(
                            stringResource(R.string.debug_streams_header, state.video.streams.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(state.video.streams) { StreamRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetryClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetryClick) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun VideoHeader(video: ExtractedVideo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(video.title, style = MaterialTheme.typography.titleLarge)
        Text(video.uploaderName, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.debug_duration, video.durationSeconds / 60, video.durationSeconds % 60),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(
                R.string.debug_manifests,
                stringResource(if (video.dashManifestUrl != null) R.string.yes else R.string.no),
                stringResource(if (video.hlsManifestUrl != null) R.string.yes else R.string.no),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StreamRow(stream: MediaStream) {
    val kind = stringResource(
        when (stream.kind) {
            MediaStream.Kind.MUXED -> R.string.stream_kind_muxed
            MediaStream.Kind.VIDEO_ONLY -> R.string.stream_kind_video_only
            MediaStream.Kind.AUDIO -> R.string.stream_kind_audio
        },
    )
    val unknown = stringResource(R.string.unknown)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.stream_title, kind, stream.qualityLabel.ifEmpty { unknown }),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(
                    R.string.stream_details,
                    stream.format ?: unknown,
                    stream.codec ?: unknown,
                    stream.bitrate / 1000,
                    stream.delivery.name,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            stream.audioTrackName?.let {
                Text(stringResource(R.string.stream_audio_track, it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugExtractionPreview() {
    ItubeProTheme {
        DebugExtractionContent(
            url = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            state = DebugUiState.Success(
                ExtractedVideo(
                    id = "jNQXAC9IVRw",
                    url = "",
                    title = "Me at the zoo",
                    uploaderName = "jawed",
                    durationSeconds = 19,
                    dashManifestUrl = null,
                    hlsManifestUrl = null,
                    streams = listOf(
                        MediaStream(
                            id = "0",
                            kind = MediaStream.Kind.VIDEO_ONLY,
                            delivery = MediaStream.Delivery.PROGRESSIVE,
                            content = "",
                            format = "MPEG-4",
                            codec = "avc1.4d401e",
                            qualityLabel = "480p",
                            bitrate = 500_000,
                            height = 480,
                            audioTrackName = null,
                        ),
                    ),
                ),
            ),
            onUrlChange = {},
            onExtractClick = {},
            onRetryClick = {},
        )
    }
}
