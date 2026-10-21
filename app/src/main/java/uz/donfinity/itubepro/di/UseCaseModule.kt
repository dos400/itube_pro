package uz.donfinity.itubepro.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import uz.donfinity.itubepro.domain.repository.SearchRepository
import uz.donfinity.itubepro.domain.repository.VideoRepository
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
}
