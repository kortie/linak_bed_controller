package dev.linakbed.app.protolab

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.di.ApplicationScope
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.BedControllerConfig
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.controller.DefaultBedController
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.ExecutionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Named
import javax.inject.Singleton

/** Protocol Lab의 Controller 탭이 움직일 침대. 앱의 침대 목록과 별개다 (S13). */
class LabTarget {
    val device = MutableStateFlow<PairedDevice?>(null)
}

@Module
@InstallIn(SingletonComponent::class)
object ProtocolLabModule {
    @Provides
    fun provideDeviceScanner(
        @ApplicationContext context: Context,
    ): DeviceScanner = AndroidBleScanner(context)

    @Provides
    @Singleton
    fun provideLabTarget(): LabTarget = LabTarget()

    /** Lab 전용 컨트롤러: Lab의 원시 링크 위에서 안전 감독을 거쳐 [LabTarget]을 움직인다. */
    @Provides
    @Singleton
    @Named(LAB)
    fun provideLabController(
        @ApplicationScope scope: CoroutineScope,
        link: AndroidBedLink,
        target: LabTarget,
        executionLog: ExecutionLog,
        environment: ControllerEnvironment,
    ): BedController = DefaultBedController(scope, link, { target.device.value }, executionLog, BedControllerConfig(), environment)

    const val LAB = "lab"
}
