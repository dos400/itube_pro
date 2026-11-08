package uz.donfinity.itubepro.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.donfinity.itubepro.BuildConfig
import uz.donfinity.itubepro.domain.model.AppInfo

/** BuildConfig only exists in :app, so this is where the domain's [AppInfo] gets its values. */
@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {

    @Provides
    fun provideAppInfo() = AppInfo(
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        updateRepo = BuildConfig.UPDATE_REPO,
    )
}
