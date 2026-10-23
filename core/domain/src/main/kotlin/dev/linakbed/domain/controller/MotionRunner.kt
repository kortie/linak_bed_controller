package dev.linakbed.domain.controller

import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.safety.SafetyReason
import dev.linakbed.domain.safety.SafetySupervisor
import dev.linakbed.domain.safety.SensorBaseline
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.CommandTiming
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * [CommandTiming]을 해석하는 유일한 곳 (ADR-004): OneShot은 1회 쓰기, Repeat은 끝날 때까지 주기적으로 다시 쓴다.
 * 동작은 감시자([SafetySupervisor])가 끝낼 이유를 낼 때까지, 쓰기가 실패할 때까지, 또는 [run]의 maxDuration(S1)까지 계속된다.
 * 끝난 뒤 Stop을 보내는 것은 호출자(DefaultBedController) 몫이다.
 */
internal class MotionRunner(
    private val link: BedLink,
    private val supervisor: SafetySupervisor,
) {
    suspend fun run(
        command: BedCommand,
        kind: MotionKind,
        timing: CommandTiming,
        maxDuration: Duration,
        baseline: SensorBaseline,
    ): MotionOutcome =
        withTimeoutOrNull(maxDuration) {
            coroutineScope {
                val end = CompletableDeferred<MotionOutcome>()
                val writer = launch { write(command, timing)?.let(end::complete) }
                val watcher = launch { end.complete(supervisor.watch(link, kind, baseline)) }
                end.await().also {
                    writer.cancel()
                    watcher.cancel()
                }
            }
        } ?: MotionOutcome.SafetyStopped(SafetyReason.MAX_DURATION)

    /** 쓰기가 실패하면 그 이유, OneShot을 무사히 썼으면 null (이후는 감시자가 끝낸다). */
    private suspend fun write(
        command: BedCommand,
        timing: CommandTiming,
    ): MotionOutcome? =
        when (timing) {
            CommandTiming.OneShot -> link.write(command).failureOutcome()
            is CommandTiming.Repeat -> {
                var failure: MotionOutcome? = null
                while (failure == null) {
                    failure = link.write(command).failureOutcome()
                    if (failure == null) delay(timing.interval)
                }
                failure
            }
        }
}
