package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.SearchPage

interface SearchRepository {
    /** @throws AppException */
    suspend fun suggestions(query: String): List<String>

    /** First page of video results. @throws AppException */
    suspend fun search(query: String): SearchPage

    /**
     * The page after the one that returned [page]. [query] must be the same query.
     *
     * @throws AppException
     */
    suspend fun searchMore(query: String, page: PageToken): SearchPage
}
