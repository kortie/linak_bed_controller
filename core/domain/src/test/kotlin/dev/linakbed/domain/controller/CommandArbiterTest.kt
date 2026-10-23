package dev.linakbed.domain.controller

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.controller.CommandArbiter.Decision
import org.junit.jupiter.api.Test

/** 아키텍처 §6 중재 표의 결정 부분 (STOP 열은 DefaultBedControllerTest). */
class CommandArbiterTest {
    @Test
    fun `ARB idle - any source starts`() {
        assertThat(CommandArbiter.decide(null, CommandSource.MANUAL)).isEqualTo(Decision.Start)
        assertThat(CommandArbiter.decide(null, CommandSource.REMOTE)).isEqualTo(Decision.Start)
    }

    @Test
    fun `ARB MANUAL active - MANUAL replaces, REMOTE is rejected`() {
        assertThat(CommandArbiter.decide(CommandSource.MANUAL, CommandSource.MANUAL)).isEqualTo(Decision.Replace)
        assertThat(CommandArbiter.decide(CommandSource.MANUAL, CommandSource.REMOTE)).isEqualTo(Decision.Reject(CommandSource.MANUAL))
    }

    @Test
    fun `ARB REMOTE active - both MANUAL and newer REMOTE replace`() {
        assertThat(CommandArbiter.decide(CommandSource.REMOTE, CommandSource.MANUAL)).isEqualTo(Decision.Replace)
        assertThat(CommandArbiter.decide(CommandSource.REMOTE, CommandSource.REMOTE)).isEqualTo(Decision.Replace)
    }
}
