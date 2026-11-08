package uz.donfinity.itubepro.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uz.donfinity.itubepro.R

/** Gear icon for the top bar of the top-level tabs, so Settings is always one tap away. */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
    }
}
