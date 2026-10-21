package uz.donfinity.itubepro.data.mapper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
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
        return PageToken(writeObject(page!!))
    }

    fun decode(token: PageToken): Page = readObject(token) as Page

    /**
     * Channel videos need the tab's link handler as well as the Page to load more. Both are
     * Serializable, so they travel together in one token.
     */
    fun encodeWithTab(tab: ListLinkHandler, page: Page?): PageToken? {
        if (!Page.isValid(page)) return null
        return PageToken(writeObject(arrayOf(tab, page)))
    }

    fun decodeWithTab(token: PageToken): Pair<ListLinkHandler, Page> {
        val parts = readObject(token) as Array<*>
        return parts[0] as ListLinkHandler to parts[1] as Page
    }

    private fun writeObject(value: Any): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(value) }
            buffer.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun readObject(token: PageToken): Any {
        val bytes = Base64.getDecoder().decode(token.value)
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
}
