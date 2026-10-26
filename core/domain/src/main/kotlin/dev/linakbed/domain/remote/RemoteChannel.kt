package dev.linakbed.domain.remote

/** 원격 명령이 들어온 경로 (ADR-006). 실행 기록이 참조하므로 원격 공통 층 중 가장 먼저 정의한다. */
enum class RemoteChannel {
    /** SmartThings 루틴 "알림 보내기" (ADR-002, 02 문서 Part B). */
    SMARTTHINGS_NOTIFICATION,

    /** 앱 바로가기 — 삼성 모드 및 루틴의 "앱 작업", 런처 길게 누르기 (ADR-011, 02 문서 Part C). */
    APP_SHORTCUT,
}
