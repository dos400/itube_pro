package uz.donfinity.itubepro.ui.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.SegmentCategory
import uz.donfinity.itubepro.ui.common.labelRes

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val audioOnly by viewModel.audioOnly.collectAsStateWithLifecycle()
    val autoplayNext by viewModel.autoplayNext.collectAsStateWithLifecycle()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorBlockCategories by viewModel.sponsorBlockCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Runs on every ON_RESUME, e.g. when coming back from the system battery dialog.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    SettingsContent(
        audioOnly = audioOnly,
        autoplayNext = autoplayNext,
        batteryUnrestricted = batteryUnrestricted,
        sponsorBlockEnabled = sponsorBlockEnabled,
        sponsorBlockCategories = sponsorBlockCategories,
        onBack = onBack,
        onAudioOnlyChange = viewModel::onAudioOnlyChange,
        onAutoplayNextChange = viewModel::onAutoplayNextChange,
        onSponsorBlockEnabledChange = viewModel::onSponsorBlockEnabledChange,
        onSponsorBlockCategoryToggle = viewModel::onSponsorBlockCategoryToggle,
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
    autoplayNext: Boolean,
    batteryUnrestricted: Boolean,
    sponsorBlockEnabled: Boolean,
    sponsorBlockCategories: Set<SegmentCategory>,
    onBack: () -> Unit,
    onAudioOnlyChange: (Boolean) -> Unit,
    onAutoplayNextChange: (Boolean) -> Unit,
    onSponsorBlockEnabledChange: (Boolean) -> Unit,
    onSponsorBlockCategoryToggle: (SegmentCategory) -> Unit,
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
        // Scrollable: with SponsorBlock's categories expanded the list is taller than a phone screen.
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_audio_only)) },
                supportingContent = { Text(stringResource(R.string.settings_audio_only_summary)) },
                leadingContent = { Icon(Icons.Outlined.Headphones, contentDescription = null) },
                trailingContent = { Switch(checked = audioOnly, onCheckedChange = onAudioOnlyChange) },
                modifier = Modifier.clickable { onAudioOnlyChange(!audioOnly) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_autoplay)) },
                supportingContent = { Text(stringResource(R.string.settings_autoplay_summary)) },
                leadingContent = { Icon(Icons.Outlined.SkipNext, contentDescription = null) },
                trailingContent = { Switch(checked = autoplayNext, onCheckedChange = onAutoplayNextChange) },
                modifier = Modifier.clickable { onAutoplayNextChange(!autoplayNext) },
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
                headlineContent = { Text(stringResource(R.string.settings_sponsorblock)) },
                supportingContent = { Text(stringResource(R.string.settings_sponsorblock_summary)) },
                leadingContent = { Icon(Icons.Outlined.FastForward, contentDescription = null) },
                trailingContent = { Switch(checked = sponsorBlockEnabled, onCheckedChange = onSponsorBlockEnabledChange) },
                modifier = Modifier.clickable { onSponsorBlockEnabledChange(!sponsorBlockEnabled) },
            )
            if (sponsorBlockEnabled) {
                SegmentCategory.entries.forEach { category ->
                    ListItem(
                        headlineContent = { Text(stringResource(category.labelRes())) },
                        leadingContent = {
                            Checkbox(
                                checked = category in sponsorBlockCategories,
                                onCheckedChange = { onSponsorBlockCategoryToggle(category) },
                            )
                        },
                        modifier = Modifier
                            .padding(start = 40.dp)
                            .clickable { onSponsorBlockCategoryToggle(category) },
                    )
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.debug_title)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDebug),
            )
        }
    }
}
