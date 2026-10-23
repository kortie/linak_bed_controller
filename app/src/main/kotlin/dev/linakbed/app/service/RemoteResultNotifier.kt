package dev.linakbed.app.service

import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.shouldNotifyUser
import kotlinx.coroutines.flow.first

/** 실패는 항상, 성공은 설정에 따라, 거절은 알리지 않는다 (02 문서 Part B §4). */
class RemoteResultNotifier(
    private val notifications: BedNotifications,
    private val settings: RemoteSettingsStore,
) : ResultNotifier {
    override suspend fun onOutcome(
        request: RemoteRequest,
        outcome: RemoteOutcome,
    ) {
        if (outcome.shouldNotifyUser(settings.settings().first())) notifications.showResult(request, outcome)
    }
}
