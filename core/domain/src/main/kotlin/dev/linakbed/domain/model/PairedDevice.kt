package dev.linakbed.domain.model

/** 페어링한 침대. [bedId]는 페어링 때 만드는 UUID로, 향후 클라우드 등록 ID로도 쓴다 (ADR-006). */
data class PairedDevice(
    val bedId: String,
    val address: String,
    val name: String?,
    val cdmAssociationId: Int,
)
