package dev.linakbed.domain.controller

/** 명령 출처와 중재 우선순위 (아키텍처 §6). 사람이 직접 조작하는 MANUAL이 원격보다 우선한다. */
enum class CommandSource(
    val priority: Int,
) {
    MANUAL(2),
    REMOTE(1),
}
