package uz.donfinity.itubepro.ui.common

import android.icu.text.CompactDecimalFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import uz.donfinity.itubepro.R

/** Pure formatting helpers. They follow the device locale and contain no app logic. */
object Formatters {

    /** 75 → "1:15", 3723 → "1:02:03". */
    fun duration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** 1_234_567 → "1.2M" (or "123만" in Korean, etc.). */
    fun compactCount(count: Long): String =
        CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
            .format(count)

    /** "3 hours ago", "2 days ago"… in the device language. */
    fun relative(instant: Instant): String =
        DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()

    fun date(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)
}

/**
 * "1 view" / "1.2M views". The plural form is picked from the real number, while the
 * displayed number is the compact one.
 */
@Composable
fun viewCountText(count: Long): String =
    pluralStringResource(R.plurals.view_count, count.toPluralQuantity(), Formatters.compactCount(count))

@Composable
fun subscriberCountText(count: Long): String =
    pluralStringResource(R.plurals.subscriber_count, count.toPluralQuantity(), Formatters.compactCount(count))

// Plural rules only need to know "one vs other" etc., so clamping huge numbers is safe.
private fun Long.toPluralQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
