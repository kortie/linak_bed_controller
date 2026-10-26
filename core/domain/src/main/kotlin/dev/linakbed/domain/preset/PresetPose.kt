package dev.linakbed.domain.preset

/** 프리셋에 도착(또는 저장)했을 때의 센서 높이 (S12). 프리셋 버튼 그림에 쓴다. */
data class PresetPose(
    val headCm: Double,
    val feetCm: Double,
)
