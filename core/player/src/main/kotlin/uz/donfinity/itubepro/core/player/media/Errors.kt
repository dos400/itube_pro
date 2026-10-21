package uz.donfinity.itubepro.core.player.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

private const val HTTP_FORBIDDEN = 403

/** True if this exception, or any of its causes, is an HTTP 403 from a data source. */
@OptIn(UnstableApi::class)
internal fun Throwable.isHttpForbidden(): Boolean =
    generateSequence(this) { it.cause }
        .any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == HTTP_FORBIDDEN }
