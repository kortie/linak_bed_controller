package dev.linakbed.domain

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.safety.SafetyReason
import org.junit.jupiter.api.Test

class BedResultTest {
    @Test
    fun `errors and results are value types`() {
        assertThat(BedResult.Ok(1)).isEqualTo(BedResult.Ok(1))
        assertThat(BedResult.Err(BedError.WriteFailed(3))).isEqualTo(BedResult.Err(BedError.WriteFailed(3)))
        assertThat(BedError.GattFailure(GattStage.WRITE, null)).isNotEqualTo(BedError.GattFailure(GattStage.WRITE, 1))
        assertThat(BedError.Busy(CommandSource.MANUAL).activeSource).isEqualTo(CommandSource.MANUAL)
        assertThat(BedError.SafetyStop(SafetyReason.STALL).reason).isEqualTo(SafetyReason.STALL)
        assertThat(BedError.PermissionMissing("p").permission).isEqualTo("p")
        assertThat(LinkState.Failed(BedError.NotPaired).error).isEqualTo(BedError.NotPaired)
    }

    @Test
    fun `andThen continues on success and short-circuits on failure`() {
        var called = false

        assertThat(BedResult.Ok(2).andThen { BedResult.Ok(it * 10) }).isEqualTo(BedResult.Ok(20))
        val failed: BedResult<Int> = BedResult.Err(BedError.Disconnected)
        assertThat(
            failed.andThen {
                called = true
                BedResult.Ok(it)
            },
        ).isEqualTo(BedResult.Err(BedError.Disconnected))
        assertThat(called).isFalse()
    }

    @Test
    fun `manual commands outrank remote ones`() {
        assertThat(CommandSource.MANUAL.priority).isGreaterThan(CommandSource.REMOTE.priority)
    }
}
