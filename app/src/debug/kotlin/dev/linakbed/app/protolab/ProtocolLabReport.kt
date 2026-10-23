package dev.linakbed.app.protolab

import dev.linakbed.domain.log.LogRedaction
import dev.linakbed.protocol.HeightReading

/** "결과 복사" 텍스트 — docs/spec/hw-findings.md에 옮겨 적기 위한 요약 + 로그. MAC 주소는 가린다. */
object ProtocolLabReport {
    fun render(state: ProtocolLabUiState): String =
        buildString {
            appendLine("# Protocol Lab report")
            appendLine("device: ${state.selectedAddress?.let(LogRedaction::mac) ?: "-"}")
            state.devices.firstOrNull { it.address == state.selectedAddress }?.let { device ->
                appendLine(
                    "advertised name: ${device.name ?: "-"}, control service UUID in advertisement: ${device.advertisesControlService}",
                )
            }
            appendLine("link: ${state.link}")
            appendLine("services:")
            state.services.forEach { service ->
                appendLine("  ${service.uuid}")
                service.characteristics.forEach { appendLine("    ${it.uuid} ${it.properties.sorted()}") }
            }
            appendLine("head: ${state.head.describe()}")
            appendLine("feet: ${state.feet.describe()}")
            appendLine("log:")
            state.log.forEach { appendLine("  $it") }
        }

    private fun HeightReading?.describe(): String =
        if (this == null) "-" else "raw=$raw cm=${"%.2f".format(cm)} speed=${speedCmPerMin ?: "-"}"
}
