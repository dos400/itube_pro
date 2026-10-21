package uz.donfinity.itubepro.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import uz.donfinity.itubepro.data.error.toAppException

/**
 * Runs a blocking extractor call on [dispatcher] and turns any failure into an
 * AppException. Every repository goes through this, so error mapping happens in one place.
 */
internal suspend fun <T> extract(dispatcher: CoroutineDispatcher, block: () -> T): T =
    withContext(dispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            // Never swallow cancellation, or the coroutine keeps running after the
            // screen that started it is gone.
            throw e
        } catch (e: Exception) {
            throw e.toAppException()
        }
    }
