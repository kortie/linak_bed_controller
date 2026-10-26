package dev.linakbed.domain.remote

/** 원격 명령이 들어온 경로 (ADR-006). 실행 기록이 참조하므로 원격 공통 층 중 가장 먼저 정의한다. */
enum class RemoteChannel {
    /** SmartThings 루틴 알림 (ADR-002) — S14에서 제거(ADR-013). 예전 실행 기록을 읽으려고 남긴다. 항상 꺼짐. */
    SMARTTHINGS_NOTIFICATION,

    /** 앱 바로가기 — 삼성 모드 및 루틴의 "앱 작업", 런처 길게 누르기 (ADR-011, 02 문서 Part C). */
    APP_SHORTCUT,
}
