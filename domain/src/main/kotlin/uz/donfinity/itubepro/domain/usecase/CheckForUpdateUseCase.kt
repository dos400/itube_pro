package uz.donfinity.itubepro.domain.usecase

import uz.donfinity.itubepro.domain.model.AppInfo
import uz.donfinity.itubepro.domain.model.AppRelease
import uz.donfinity.itubepro.domain.repository.AppUpdateRepository

/**
 * Returns the latest release if it's newer than the running build, otherwise null.
 * Without an update repo configured it never asks the network.
 */
class CheckForUpdateUseCase(
    private val repository: AppUpdateRepository,
    private val appInfo: AppInfo,
) {
    suspend operator fun invoke(): AppRelease? {
        if (appInfo.updateRepo.isBlank()) return null
        return repository.latestRelease(appInfo.updateRepo)?.takeIf { it.versionCode > appInfo.versionCode }
    }
}
