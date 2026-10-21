package uz.donfinity.itubepro.ui.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.donfinity.itubepro.R

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val audioOnly by viewModel.audioOnly.collectAsStateWithLifecycle()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Runs on every ON_RESUME, e.g. when coming back from the system battery dialog.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    SettingsContent(
        audioOnly = audioOnly,
        batteryUnrestricted = batteryUnrestricted,
        onBack = onBack,
        onAudioOnlyChange = viewModel::onAudioOnlyChange,
        onBatteryClick = {
            // Some vendor ROMs remove the dialog; fall back to the generic settings list.
            try {
                context.startActivity(viewModel.batteryIntent())
            } catch (_: ActivityNotFoundException) {
                context.startActivity(viewModel.batteryFallbackIntent())
            }
        },
        onOpenDebug = onOpenDebug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    audioOnly: Boolean,
    batteryUnrestricted: Boolean,
    onBack: () -> Unit,
    onAudioOnlyChange: (Boolean) -> Unit,
    onBatteryClick: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_audio_only)) },
                supportingContent = { Text(stringResource(R.string.settings_audio_only_summary)) },
                leadingContent = { Icon(Icons.Outlined.Headphones, contentDescription = null) },
                trailingContent = { Switch(checked = audioOnly, onCheckedChange = onAudioOnlyChange) },
                modifier = Modifier.clickable { onAudioOnlyChange(!audioOnly) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_battery)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (batteryUnrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_restricted,
                        ),
                    )
                },
                leadingContent = {
                    Icon(
                        if (batteryUnrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onBatteryClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.debug_title)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDebug),
            )
        }
    }
}
