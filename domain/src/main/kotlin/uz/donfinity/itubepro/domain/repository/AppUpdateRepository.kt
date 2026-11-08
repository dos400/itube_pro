package uz.donfinity.itubepro.domain.repository

import uz.donfinity.itubepro.domain.model.AppRelease

interface AppUpdateRepository {
    /** The newest published release of [repo] ("owner/name"), or null if it has none with an APK. */
    suspend fun latestRelease(repo: String): AppRelease?
}
