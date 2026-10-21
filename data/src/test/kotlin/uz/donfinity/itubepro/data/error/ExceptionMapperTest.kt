package uz.donfinity.itubepro.data.error

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import uz.donfinity.itubepro.domain.error.AppException

/**
 * Guards the `when` order in ExceptionMapper. Almost all NewPipe exceptions extend
 * ParsingException, so a subclass checked too late would silently become "Parsing".
 */
class ExceptionMapperTest {

    @Test
    fun `subclasses of ParsingException map to their specific error`() {
        assertTrue(AgeRestrictedContentException("x").toAppException() is AppException.AgeRestricted)
        assertTrue(GeographicRestrictionException("x").toAppException() is AppException.GeoRestricted)
        assertTrue(PrivateContentException("x").toAppException() is AppException.Unavailable)
        assertTrue(ContentNotAvailableException("x").toAppException() is AppException.Unavailable)
    }

    @Test
    fun `plain parsing error means the extractor needs an update`() {
        assertTrue(ParsingException("x").toAppException() is AppException.Parsing)
    }

    @Test
    fun `captcha and network errors`() {
        assertTrue(ReCaptchaException("x", "https://youtube.com").toAppException() is AppException.RateLimited)
        assertTrue(SocketTimeoutException().toAppException() is AppException.Network)
        assertTrue(IOException().toAppException() is AppException.Network)
    }

    @Test
    fun `AppException passes through unchanged and unknown errors are Unknown`() {
        val original = AppException.InvalidUrl()
        assertTrue(original.toAppException() === original)
        assertTrue(IllegalStateException().toAppException() is AppException.Unknown)
    }
}
