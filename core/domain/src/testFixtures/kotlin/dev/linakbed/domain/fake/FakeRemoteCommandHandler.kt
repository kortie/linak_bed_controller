package dev.linakbed.domain.fake

import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult

class FakeRemoteCommandHandler : RemoteCommandHandler {
    data class Rejection(
        val channel: RemoteChannel,
        val requestId: String,
        val reason: RejectReason,
        val detail: String?,
    )

    val handled = mutableListOf<RemoteRequest>()
    val rejected = mutableListOf<Rejection>()
    var result: RemoteResult = RemoteResult.Executed

    override suspend fun handle(request: RemoteRequest): RemoteOutcome {
        handled += request
        return RemoteOutcome(request.requestId, result)
    }

    override suspend fun reject(
        channel: RemoteChannel,
        requestId: String,
        reason: RejectReason,
        detail: String?,
    ) {
        rejected += Rejection(channel, requestId, reason, detail)
    }
}
