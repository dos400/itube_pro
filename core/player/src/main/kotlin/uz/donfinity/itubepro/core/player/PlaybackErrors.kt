package uz.donfinity.itubepro.core.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionError
import uz.donfinity.itubepro.domain.error.AppException

/**
 * Sends an [AppException] from the service to controllers. Exceptions can't cross a
 * process boundary, but a [SessionError] Bundle can, so we pack a stable code into it.
 *
 * SessionError is marked @UnstableApi in Media3 (its API may still change), hence the opt-in.
 */
@OptIn(UnstableApi::class)
internal object PlaybackErrors {

    private const val KEY_KIND = "app_error_kind"

    fun toSessionError(e: AppException): SessionError {
        // Exhaustive `when` over the sealed class: adding a new AppException is a compile
        // error here until it gets a code.
        val kind = when (e) {
            is AppException.Network -> "network"
            is AppException.RateLimited -> "rate_limited"
            is AppException.InvalidUrl -> "invalid_url"
            is AppException.AgeRestricted -> "age_restricted"
            is AppException.GeoRestricted -> "geo_restricted"
            is AppException.Unavailable -> "unavailable"
            is AppException.Parsing -> "parsing"
            is AppException.LiveNotSupported -> "live_not_supported"
            is AppException.Unknown -> "unknown"
        }
        return SessionError(SessionError.ERROR_IO, e.message.orEmpty(), Bundle().apply { putString(KEY_KIND, kind) })
    }

    fun fromSessionError(error: SessionError): AppException = when (error.extras.getString(KEY_KIND)) {
        "network" -> AppException.Network()
        "rate_limited" -> AppException.RateLimited()
        "invalid_url" -> AppException.InvalidUrl()
        "age_restricted" -> AppException.AgeRestricted()
        "geo_restricted" -> AppException.GeoRestricted()
        "unavailable" -> AppException.Unavailable()
        "parsing" -> AppException.Parsing()
        "live_not_supported" -> AppException.LiveNotSupported()
        else -> AppException.Unknown()
    }
}
