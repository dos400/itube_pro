package uz.donfinity.itubepro.domain.error

/**
 * The only failures the domain and UI layers know about. The data layer converts
 * library exceptions (NewPipe, OkHttp, ...) into one of these, so the UI never has to
 * import a third-party exception type to decide what message to show.
 */
sealed class AppException(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** No connection, timeout, DNS failure. Retrying later usually works. */
    class Network(cause: Throwable? = null) : AppException(cause?.message, cause)

    /** YouTube is asking for a reCAPTCHA / "confirm you're not a bot" (HTTP 429). */
    class RateLimited(cause: Throwable? = null) : AppException(cause?.message, cause)

    /** The URL is not a YouTube video URL we can handle. */
    class InvalidUrl(cause: Throwable? = null) : AppException(cause?.message, cause)

    class AgeRestricted(cause: Throwable? = null) : AppException(cause?.message, cause)

    class GeoRestricted(cause: Throwable? = null) : AppException(cause?.message, cause)

    /** Private, deleted, members-only, paid or otherwise unavailable content. */
    class Unavailable(cause: Throwable? = null) : AppException(cause?.message, cause)

    /**
     * The extractor could not understand YouTube's response. This usually means YouTube
     * changed something and NewPipeExtractor needs updating (see CLAUDE.md).
     */
    class Parsing(cause: Throwable? = null) : AppException(cause?.message, cause)

    class Unknown(cause: Throwable? = null) : AppException(cause?.message, cause)
}
