package uz.donfinity.itubepro.data.mapper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64
import org.schabi.newpipe.extractor.Page
import uz.donfinity.itubepro.domain.model.PageToken

/**
 * NewPipe's [Page] (URL, ids, cookies, POST body) implements java.io.Serializable, so we
 * turn it into Base64 text and wrap that in a domain [PageToken]. The repository then
 * doesn't need to remember any paging state.
 *
 * Tokens are only valid for the current app version, because Java serialization breaks
 * when the extractor's classes change. That's fine: a token only lives until the next
 * page is loaded, and we never persist it.
 */
internal object PageTokenCodec {

    fun encode(page: Page?): PageToken? {
        if (!Page.isValid(page)) return null
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(page) }
            buffer.toByteArray()
        }
        return PageToken(Base64.getEncoder().encodeToString(bytes))
    }

    fun decode(token: PageToken): Page {
        val bytes = Base64.getDecoder().decode(token.value)
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Page }
    }
}
