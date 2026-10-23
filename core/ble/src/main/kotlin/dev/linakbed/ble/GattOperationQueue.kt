package dev.linakbed.ble

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * GATT 작업을 한 번에 하나씩 실행한다 (Android GATT는 동시 호출을 허용하지 않음).
 * 각 작업은 "시작 → 맞는 콜백 이벤트 대기"이며, 작업마다 시간 제한이 있다.
 *
 * [close] 후에는 진행 중·대기 중 작업이 모두 [BedError.Disconnected]로 끝나고, [open] 전까지 새 작업도 거절된다.
 * [onEvent]는 GATT 콜백 스레드에서 불린다.
 */
class GattOperationQueue {
    private val mutex = Mutex()
    private val lock = Any()
    private var pending: Pending<*>? = null
    private var closed = true

    private class Pending<T>(
        val matcher: (GattEvent) -> BedResult<T>?,
        val result: CompletableDeferred<BedResult<T>>,
    ) {
        fun tryComplete(event: GattEvent): Boolean {
            val outcome = matcher(event) ?: return false
            result.complete(outcome)
            return true
        }

        fun fail(error: BedError) {
            result.complete(BedResult.Err(error))
        }
    }

    fun open() {
        synchronized(lock) { closed = false }
    }

    fun close(error: BedError = BedError.Disconnected) {
        synchronized(lock) {
            closed = true
            pending?.fail(error)
            pending = null
        }
    }

    /**
     * @param start GATT 호출을 시작한다. 시작조차 못 하면 그 이유를 돌려준다 (null = 시작됨).
     * @param matcher 이 작업의 결과 이벤트면 결과를, 아니면 null.
     */
    suspend fun <T> run(
        timeout: Duration,
        timeoutError: BedError,
        start: () -> BedError?,
        matcher: (GattEvent) -> BedResult<T>?,
    ): BedResult<T> =
        mutex.withLock {
            val outcome = CompletableDeferred<BedResult<T>>()
            val current = Pending(matcher, outcome)
            synchronized(lock) {
                if (closed) return@withLock BedResult.Err(BedError.Disconnected)
                pending = current
            }
            val startError = start()
            val result =
                if (startError != null) {
                    BedResult.Err(startError)
                } else {
                    withTimeoutOrNull(timeout) { outcome.await() } ?: BedResult.Err(timeoutError)
                }
            synchronized(lock) { if (pending === current) pending = null }
            result
        }

    fun onEvent(event: GattEvent) {
        synchronized(lock) {
            val current = pending ?: return
            if (current.tryComplete(event)) pending = null
        }
    }
}
