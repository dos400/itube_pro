package uz.donfinity.itubepro.domain.usecase

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.SearchPage
import uz.donfinity.itubepro.domain.repository.SearchRepository

class GetSearchSuggestionsUseCase(private val repository: SearchRepository) {

    /** Returns an empty list for blank input instead of hitting the network. */
    suspend operator fun invoke(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return repository.suggestions(trimmed)
    }
}

class SearchVideosUseCase(private val repository: SearchRepository) {

    /**
     * @param page null for the first page, otherwise the token from the previous page.
     * @throws AppException
     */
    suspend operator fun invoke(query: String, page: PageToken? = null): SearchPage {
        val trimmed = query.trim()
        return if (page == null) repository.search(trimmed) else repository.searchMore(trimmed, page)
    }
}
