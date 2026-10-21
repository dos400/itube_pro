package uz.donfinity.itubepro.data.mapper

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Combines several single-stream DASH manifests into one manifest with one AdaptationSet
 * holding all their Representations (qualities).
 *
 * NewPipe's YoutubeProgressiveDashManifestCreator makes one complete MPD per stream:
 *
 *   MPD > Period > AdaptationSet > Representation (BaseURL + SegmentBase)
 *
 * With all qualities in one AdaptationSet, ExoPlayer sees them as interchangeable versions
 * of the same track. It can then adapt to bandwidth ("Auto") or switch on request without
 * rebuilding the player. Each Representation keeps its own BaseURL (each quality is a
 * separate file on YouTube's servers), which DASH allows.
 */
internal object DashManifestMerger {

    /** @param manifests at least one; all must be the same media type (all video, same container). */
    fun merge(manifests: List<String>): String {
        require(manifests.isNotEmpty())
        if (manifests.size == 1) return manifests.single()

        val documents = manifests.map(::parse)
        val target = documents.first()
        val targetSet = target.firstElement("AdaptationSet")
        val seenIds = target.elements("Representation").map { it.getAttribute("id") }.toMutableSet()

        for (doc in documents.drop(1)) {
            for (representation in doc.elements("Representation")) {
                // ids are itags, so a duplicate means the same stream twice. Skip it.
                if (!seenIds.add(representation.getAttribute("id"))) continue
                targetSet.appendChild(target.importNode(representation, true))
            }
        }
        return serialize(target)
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The input is generated locally, but refusing DTDs is the safe default for any
            // XML parsing (prevents XXE attacks). The desktop JDK supports this Xerces feature.
            // Android's own parser doesn't (it throws), and it never loads external entities
            // anyway, so a failure here is safe to ignore.
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: ParserConfigurationException) {
            }
        }
        return factory.newDocumentBuilder().parse(xml.byteInputStream())
    }

    private fun serialize(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        return StringWriter().also { transformer.transform(DOMSource(doc), StreamResult(it)) }.toString()
    }

    private fun Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Document.firstElement(localName: String): Element =
        elements(localName).firstOrNull() ?: error("Manifest has no <$localName>")
}
