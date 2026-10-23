package dev.linakbed.domain.remote

/** 원격 명령이 들어온 경로 (ADR-006). 실행 기록이 참조하므로 원격 공통 층 중 가장 먼저 정의한다. */
enum class RemoteChannel { SMARTTHINGS_NOTIFICATION, }
