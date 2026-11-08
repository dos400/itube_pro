package uz.donfinity.itubepro.domain.model

/**
 * Facts about the running build, provided by `:app` (from BuildConfig).
 *
 * [updateRepo] is the GitHub "owner/name" whose releases we update from (`updateRepo` in
 * gradle.properties). Empty means "don't check".
 */
data class AppInfo(
    val versionCode: Int,
    val versionName: String,
    val updateRepo: String,
    /** When this version was installed (first install or last update), epoch millis. */
    val lastUpdatedAtMillis: Long,
)

/** A GitHub release that carries an APK. Its tag is `v<versionCode>` (see release.yml). */
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** -1 if GitHub didn't say. */
    val apkSizeBytes: Long,
)
