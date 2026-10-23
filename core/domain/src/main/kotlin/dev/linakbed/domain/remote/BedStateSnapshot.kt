package dev.linakbed.domain.remote

import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.PresetSlot

/** 채널로 상태를 보고할 때의 공통 모델 (02 문서 A2). 지금은 진단 화면만 쓰고, 향후 클라우드 채널이 그대로 회신한다. */
data class BedStateSnapshot(
    val bedId: String,
    val link: LinkState,
    val headCm: Double?,
    val feetCm: Double?,
    val activeMotion: ActiveMotion?,
    val light: LightState,
    val presets: Map<PresetSlot, String>,
    val lastOutcome: RemoteOutcome?,
)

fun BedStatus.toSnapshot(
    bedId: String,
    presets: Map<PresetSlot, String>,
    lastOutcome: RemoteOutcome?,
): BedStateSnapshot =
    BedStateSnapshot(
        bedId = bedId,
        link = link,
        headCm = headCm,
        feetCm = feetCm,
        activeMotion = activeMotion,
        light = light,
        presets = presets,
        lastOutcome = lastOutcome,
    )
