package uz.donfinity.itubepro.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import uz.donfinity.itubepro.domain.model.AppInfo
import uz.donfinity.itubepro.domain.repository.AppUpdateRepository
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.SearchRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.domain.repository.TrendingRepository
import uz.donfinity.itubepro.domain.repository.VideoRepository
import uz.donfinity.itubepro.domain.usecase.CheckForUpdateUseCase
import uz.donfinity.itubepro.domain.usecase.GetRecommendationsUseCase
import uz.donfinity.itubepro.domain.usecase.GetSearchSuggestionsUseCase
import uz.donfinity.itubepro.domain.usecase.GetVideoDetailsUseCase
import uz.donfinity.itubepro.domain.usecase.GetVideoStreamsUseCase
import uz.donfinity.itubepro.domain.usecase.SearchVideosUseCase

/**
 * :domain has no DI annotations, so its use cases are constructed here. They're stateless
 * and cheap, so a new instance per ViewModel is fine (no scope annotation).
 */
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetVideoStreams(repository: VideoRepository) = GetVideoStreamsUseCase(repository)

    @Provides
    fun provideGetVideoDetails(repository: VideoRepository) = GetVideoDetailsUseCase(repository)

    @Provides
    fun provideGetSearchSuggestions(repository: SearchRepository) =
        GetSearchSuggestionsUseCase(repository)

    @Provides
    fun provideSearchVideos(repository: SearchRepository) = SearchVideosUseCase(repository)

    @Provides
    fun provideGetRecommendations(
        history: HistoryRepository,
        favorites: FavoritesRepository,
        subscriptions: SubscriptionRepository,
        videos: VideoRepository,
        trending: TrendingRepository,
    ) = GetRecommendationsUseCase(history, favorites, subscriptions, videos, trending)

    @Provides
    fun provideCheckForUpdate(repository: AppUpdateRepository, appInfo: AppInfo) =
        CheckForUpdateUseCase(repository, appInfo)
}
