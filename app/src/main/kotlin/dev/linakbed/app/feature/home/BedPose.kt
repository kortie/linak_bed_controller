package dev.linakbed.app.feature.home

/**
 * 침대 그림의 자세 (S10). 각 값은 0(가장 낮음)~1(가장 높음). 센서 값을 모르면 null.
 */
data class BedPose(
    val head: Float?,
    val feet: Float?,
) {
    val known: Boolean get() = head != null && feet != null
}

/**
 * 센서 높이(cm) → 그림 자세. 가득 올라간 높이는 **잠정값**(가상 침대와 같은 60 cm)이고,
 * 실기기 범위는 S08에서 측정해 docs/spec/hw-findings.md에 적고 여기를 고친다.
 */
object BedPoseMapper {
    const val HEAD_FULL_CM = 60.0
    const val FEET_FULL_CM = 60.0

    fun from(
        headCm: Double?,
        feetCm: Double?,
    ): BedPose = BedPose(head = fraction(headCm, HEAD_FULL_CM), feet = fraction(feetCm, FEET_FULL_CM))

    private fun fraction(
        cm: Double?,
        full: Double,
    ): Float? = cm?.let { (it / full).coerceIn(0.0, 1.0).toFloat() }
}
