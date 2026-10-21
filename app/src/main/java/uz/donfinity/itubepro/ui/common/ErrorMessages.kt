package uz.donfinity.itubepro.ui.common

import androidx.annotation.StringRes
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.error.AppException

/**
 * Maps a domain error to a string resource. UI state stores the resource ID, not the
 * text, so ViewModels never need a Context and the text follows the device language.
 */
@StringRes
fun AppException.messageRes(): Int = when (this) {
    is AppException.Network -> R.string.error_network
    is AppException.RateLimited -> R.string.error_rate_limited
    is AppException.InvalidUrl -> R.string.error_invalid_url
    is AppException.AgeRestricted -> R.string.error_age_restricted
    is AppException.GeoRestricted -> R.string.error_geo_restricted
    is AppException.Unavailable -> R.string.error_unavailable
    is AppException.Parsing -> R.string.error_parsing
    is AppException.Unknown -> R.string.error_unknown
}
