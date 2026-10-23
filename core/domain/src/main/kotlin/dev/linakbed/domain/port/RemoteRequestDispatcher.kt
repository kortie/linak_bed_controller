package dev.linakbed.domain.port

import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest

/** 원격 요청을 백그라운드에서 실행하고 결과를 돌려준다 (구현: app — 포그라운드 서비스 + 폴백). 채널과 무관하다. */
fun interface RemoteRequestDispatcher {
    suspend fun dispatch(request: RemoteRequest): RemoteOutcome
}
