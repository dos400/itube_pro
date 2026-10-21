package uz.donfinity.itubepro.data.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.mapper.PageTokenCodec
import uz.donfinity.itubepro.data.mapper.toVideoSummaries
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.SearchPage
import uz.donfinity.itubepro.domain.repository.SearchRepository

/**
 * Idea from ref/NewPipe/app/src/main/java/org/schabi/newpipe/util/ExtractorHelper.java
 * (searchFor / getMoreSearchItems / suggestionsFor).
 */
class NewPipeSearchRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun suggestions(query: String): List<String> = extract(ioDispatcher) {
        youtube.suggestionExtractor.suggestionList(query)
    }

    override suspend fun search(query: String): SearchPage = extract(ioDispatcher) {
        val info = SearchInfo.getInfo(youtube, queryHandler(query))
        SearchPage(
            items = info.relatedItems.toVideoSummaries(),
            nextPage = PageTokenCodec.encode(info.nextPage),
            suggestion = info.searchSuggestion?.takeIf { it.isNotBlank() },
            isCorrectedSearch = info.isCorrectedSearch,
        )
    }

    override suspend fun searchMore(query: String, page: PageToken): SearchPage =
        extract(ioDispatcher) {
            val more = SearchInfo.getMoreItems(youtube, queryHandler(query), PageTokenCodec.decode(page))
            SearchPage(
                items = more.items.toVideoSummaries(),
                nextPage = PageTokenCodec.encode(more.nextPage),
                suggestion = null,
                isCorrectedSearch = false,
            )
        }

    /** "videos" content filter: no channels or playlists. Empty sort = YouTube's relevance. */
    private fun queryHandler(query: String): SearchQueryHandler =
        youtube.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
}
