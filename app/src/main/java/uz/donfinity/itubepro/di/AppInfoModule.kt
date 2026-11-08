package uz.donfinity.itubepro.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.donfinity.itubepro.BuildConfig
import uz.donfinity.itubepro.domain.model.AppInfo

/** BuildConfig only exists in :app, so this is where the domain's [AppInfo] gets its values. */
@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {

    @Provides
    fun provideAppInfo(@ApplicationContext context: Context) = AppInfo(
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        updateRepo = BuildConfig.UPDATE_REPO,
        lastUpdatedAtMillis = context.packageInfo().lastUpdateTime,
    )

    private fun Context.packageInfo() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
}
