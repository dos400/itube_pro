package uz.donfinity.itubepro.data.error

import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import uz.donfinity.itubepro.domain.error.AppException

/**
 * Converts any exception thrown while extracting into an [AppException].
 *
 * Order matters: almost every NewPipe exception extends ParsingException, so the
 * specific subclasses have to be checked before the generic branch.
 */
internal fun Throwable.toAppException(): AppException = when (this) {
    is AppException -> this
    is ReCaptchaException, is SignInConfirmNotBotException -> AppException.RateLimited(this)
    is AgeRestrictedContentException -> AppException.AgeRestricted(this)
    is GeographicRestrictionException,
    is UnsupportedContentInCountryException -> AppException.GeoRestricted(this)
    is ContentNotAvailableException,
    is ContentNotSupportedException -> AppException.Unavailable(this)
    is ParsingException -> AppException.Parsing(this)
    is IOException -> AppException.Network(this)
    else -> AppException.Unknown(this)
}
